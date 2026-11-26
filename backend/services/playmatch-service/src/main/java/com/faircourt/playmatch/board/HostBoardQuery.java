package com.faircourt.playmatch.board;

import com.faircourt.playmatch.attendance.AttendanceState;
import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.game.MatchFormat;
import com.faircourt.playmatch.match.MatchState;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read projection for the host board (AD-02): game, every attendance row and the current matches, read in one
 * read-only transaction so the reads see one snapshot. Own schema only; levels come from player-service.
 */
@Component
public class HostBoardQuery {

    private final JdbcClient jdbc;

    public HostBoardQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One attendance row with the player id needed to ask player-service; never sent to the browser. */
    public record AttendanceRow(
            UUID attendanceId,
            UUID playerId,
            String displayName,
            AttendanceState state,
            Instant checkedInAt,
            Instant availableSince,
            int matchesPlayed) {}

    /** A PLANNED or ACTIVE match with its unreleased players per team. */
    public record CurrentMatch(
            UUID matchId, int matchNo, int courtNo, MatchState state, List<UUID> teamA, List<UUID> teamB) {}

    public record Snapshot(HostBoardResponse.Game game, List<AttendanceRow> attendance, List<CurrentMatch> matches) {}

    private record MatchRow(UUID matchId, int matchNo, int courtNo, MatchState state, UUID attendanceId, String team) {}

    @Transactional(readOnly = true)
    public Optional<Snapshot> read(UUID gameId) {
        Optional<HostBoardResponse.Game> game = jdbc.sql("""
						SELECT id, public_code, title, venue, starts_at, ends_at, court_count, state, match_format, min_sets
						FROM game
						WHERE id = :game""")
                .param("game", gameId)
                .query((rs, rowNum) -> new HostBoardResponse.Game(
                        rs.getObject("id", UUID.class),
                        rs.getString("public_code"),
                        rs.getString("title"),
                        rs.getString("venue"),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("court_count"),
                        GameState.valueOf(rs.getString("state")),
                        MatchFormat.valueOf(rs.getString("match_format")),
                        rs.getInt("min_sets")))
                .optional();
        if (game.isEmpty()) {
            return Optional.empty();
        }
        // Order: state group, then fewest matches played, longest waiting, id (blueprint section 14 tie-breakers).
        List<AttendanceRow> attendance = jdbc.sql("""
						SELECT id, player_id, display_name, state, checked_in_at, available_since, matches_played
						FROM attendance
						WHERE game_id = :game
						ORDER BY CASE state
						           WHEN 'PENDING' THEN 1 WHEN 'READY' THEN 2 WHEN 'ASSIGNED' THEN 3
						           WHEN 'PLAYING' THEN 4 WHEN 'RESTING' THEN 5 ELSE 6 END,
						         matches_played ASC, available_since ASC, id ASC""")
                .param("game", gameId)
                .query((rs, rowNum) -> new AttendanceRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("player_id", UUID.class),
                        rs.getString("display_name"),
                        AttendanceState.valueOf(rs.getString("state")),
                        rs.getObject("checked_in_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("available_since", OffsetDateTime.class).toInstant(),
                        rs.getInt("matches_played")))
                .list();
        return Optional.of(new Snapshot(game.get(), attendance, currentMatches(gameId)));
    }

    private List<CurrentMatch> currentMatches(UUID gameId) {
        List<MatchRow> rows = jdbc.sql("""
						SELECT m.id, m.match_no, m.court_no, m.state, mp.attendance_id, mp.team
						FROM play_match m
						LEFT JOIN match_player mp ON mp.match_id = m.id AND mp.released_at IS NULL
						WHERE m.game_id = :game AND m.state IN ('PLANNED', 'ACTIVE')
						ORDER BY m.court_no, mp.team, mp.attendance_id""")
                .param("game", gameId)
                .query((rs, rowNum) -> new MatchRow(
                        rs.getObject("id", UUID.class),
                        rs.getInt("match_no"),
                        rs.getInt("court_no"),
                        MatchState.valueOf(rs.getString("state")),
                        rs.getObject("attendance_id", UUID.class),
                        rs.getString("team")))
                .list();
        Map<UUID, MatchRow> heads = new LinkedHashMap<>();
        Map<UUID, List<UUID>> teamA = new LinkedHashMap<>();
        Map<UUID, List<UUID>> teamB = new LinkedHashMap<>();
        for (MatchRow row : rows) {
            heads.putIfAbsent(row.matchId(), row);
            teamA.computeIfAbsent(row.matchId(), k -> new ArrayList<>());
            teamB.computeIfAbsent(row.matchId(), k -> new ArrayList<>());
            if (row.attendanceId() != null) {
                ("A".equals(row.team()) ? teamA : teamB).get(row.matchId()).add(row.attendanceId());
            }
        }
        List<CurrentMatch> result = new ArrayList<>();
        for (MatchRow head : heads.values()) {
            result.add(new CurrentMatch(
                    head.matchId(),
                    head.matchNo(),
                    head.courtNo(),
                    head.state(),
                    teamA.get(head.matchId()),
                    teamB.get(head.matchId())));
        }
        return result;
    }
}
