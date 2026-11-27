package com.faircourt.playmatch.match;

import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.game.MatchFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for match suggestions (Match suggestions design 4, blueprint sections 5.1 and 14): the game, the eligible
 * pool (READY and in no unreleased match), the busy courts and each pooled player's recent partners, read in one
 * read-only transaction so they form one snapshot. Own schema only; nothing is written.
 */
@Component
public class SuggestionSnapshotQuery {

    private final JdbcClient jdbc;

    public SuggestionSnapshotQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record GameRow(GameState state, int courtCount, MatchFormat format) {}

    /** One eligible player. {@code playerId} is only used to ask player-service for the level; never sent out. */
    public record PoolPlayer(
            UUID attendanceId, UUID playerId, Instant availableSince, int matchesPlayed, Set<UUID> recentPartners) {}

    public record Snapshot(GameRow game, List<PoolPlayer> pool, List<Integer> busyCourts) {}

    private record PoolRow(UUID attendanceId, UUID playerId, Instant availableSince, int matchesPlayed) {}

    private record PartnerRow(UUID attendanceId, UUID partnerId) {}

    @Transactional(readOnly = true)
    public Optional<Snapshot> read(UUID gameId) {
        Optional<GameRow> game = jdbc.sql("SELECT state, court_count, match_format FROM game WHERE id = :game")
                .param("game", gameId)
                .query((rs, rowNum) -> new GameRow(
                        GameState.valueOf(rs.getString("state")),
                        rs.getInt("court_count"),
                        MatchFormat.valueOf(rs.getString("match_format"))))
                .optional();
        if (game.isEmpty()) {
            return Optional.empty();
        }

        List<PoolRow> rows = jdbc.sql("""
						SELECT a.id, a.player_id, a.available_since, a.matches_played
						FROM attendance a
						WHERE a.game_id = :game
						  AND a.state = 'READY'
						  AND NOT EXISTS (SELECT 1 FROM match_player mp
						                  WHERE mp.attendance_id = a.id AND mp.released_at IS NULL)
						ORDER BY a.matches_played ASC, a.available_since ASC, a.id ASC""")
                .param("game", gameId)
                .query((rs, rowNum) -> new PoolRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("player_id", UUID.class),
                        rs.getObject("available_since", OffsetDateTime.class).toInstant(),
                        rs.getInt("matches_played")))
                .list();

        List<Integer> busy =
                jdbc.sql("""
						SELECT court_no FROM play_match
						WHERE game_id = :game AND state IN ('PLANNED', 'ACTIVE')
						ORDER BY court_no""").param("game", gameId).query(Integer.class).list();

        // Last three FINISHED matches per eligible player (by finished_at, then match id); the partners are the other
        // players on the same team in those matches. CANCELLED, PLANNED and ACTIVE matches are ignored (section 14).
        List<PartnerRow> partnerRows = jdbc.sql("""
						WITH recent AS (
						  SELECT mp.attendance_id, mp.match_id, mp.team,
						         ROW_NUMBER() OVER (PARTITION BY mp.attendance_id
						                            ORDER BY m.finished_at DESC, m.id) AS rn
						  FROM match_player mp
						  JOIN play_match m ON m.id = mp.match_id
						  WHERE mp.game_id = :game
						    AND m.state = 'FINISHED'
						    AND mp.attendance_id IN (
						      SELECT a.id FROM attendance a
						      WHERE a.game_id = :game AND a.state = 'READY'
						        AND NOT EXISTS (SELECT 1 FROM match_player u
						                        WHERE u.attendance_id = a.id AND u.released_at IS NULL)))
						SELECT r.attendance_id, p.attendance_id AS partner_id
						FROM recent r
						JOIN match_player p ON p.match_id = r.match_id
						                   AND p.team = r.team
						                   AND p.attendance_id <> r.attendance_id
						WHERE r.rn <= 3""")
                .param("game", gameId)
                .query((rs, rowNum) -> new PartnerRow(
                        rs.getObject("attendance_id", UUID.class), rs.getObject("partner_id", UUID.class)))
                .list();
        Map<UUID, Set<UUID>> partners = new HashMap<>();
        for (PartnerRow row : partnerRows) {
            partners.computeIfAbsent(row.attendanceId(), k -> new HashSet<>()).add(row.partnerId());
        }

        List<PoolPlayer> pool = rows.stream()
                .map(r -> new PoolPlayer(
                        r.attendanceId(),
                        r.playerId(),
                        r.availableSince(),
                        r.matchesPlayed(),
                        partners.getOrDefault(r.attendanceId(), Set.of())))
                .toList();
        return Optional.of(new Snapshot(game.get(), pool, busy));
    }
}
