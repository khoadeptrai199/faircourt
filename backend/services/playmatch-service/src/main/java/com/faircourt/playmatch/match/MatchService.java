package com.faircourt.playmatch.match;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import com.faircourt.playmatch.attendance.AttendanceState;
import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.game.MatchFormat;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Match confirm (blueprint sections 11, 12, 15; Match confirm design 4): one local transaction that locks the game row,
 * re-reads court and attendances, inserts the match and its players and marks the players {@code ASSIGNED}. Nothing is
 * saved on any failure. Lock order: game row, then attendance rows by id.
 */
@Service
public class MatchService {

    private final JdbcClient jdbc;
    private final AccessPolicy policy;
    private final Clock clock;
    private final JsonMapper mapper;
    private final Validator validator;

    public MatchService(JdbcClient jdbc, AccessPolicy policy, Clock clock, JsonMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
        this.jdbc = jdbc;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public ConfirmMatchResponse confirm(CallerIdentity caller, UUID gameId, String body) {
        policy.requireHost(caller, gameId);
        ConfirmMatchRequest request = parseBody(body);
        GameRow game = jdbc.sql("""
                        SELECT state, court_count, match_format, next_match_no FROM game
                        WHERE id = :gameId FOR UPDATE""")
                .param("gameId", gameId)
                .query((rs, n) -> new GameRow(
                        GameState.valueOf(rs.getString("state")),
                        rs.getInt("court_count"),
                        MatchFormat.valueOf(rs.getString("match_format")),
                        rs.getInt("next_match_no")))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (game.state() == GameState.CLOSED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        MatchLineup lineup = MatchLineup.of(request.teams().A(), request.teams().B(), game.format());

        int courtNo = request.courtNo();
        if (courtNo > game.courtCount()) {
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }
        int busy = jdbc.sql("""
                        SELECT count(*) FROM play_match
                        WHERE game_id = :gameId AND court_no = :courtNo AND state IN ('PLANNED', 'ACTIVE')""")
                .param("gameId", gameId)
                .param("courtNo", courtNo)
                .query(Integer.class)
                .single();
        if (busy > 0) {
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }

        List<UUID> ids = lineup.all();
        List<AttendanceState> states = jdbc.sql("""
                        SELECT state FROM attendance
                        WHERE game_id = :gameId AND id IN (:ids) ORDER BY id FOR UPDATE""")
                .param("gameId", gameId)
                .param("ids", ids)
                .query((rs, n) -> AttendanceState.valueOf(rs.getString("state")))
                .list();
        if (states.size() != ids.size() || states.stream().anyMatch(s -> !s.canBeAssigned())) {
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }
        int unreleased = jdbc.sql("""
                        SELECT count(*) FROM match_player
                        WHERE game_id = :gameId AND attendance_id IN (:ids) AND released_at IS NULL""")
                .param("gameId", gameId)
                .param("ids", ids)
                .query(Integer.class)
                .single();
        if (unreleased > 0) {
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }

        UUID matchId = UUID.randomUUID();
        try {
            jdbc.sql("""
                            INSERT INTO play_match (id, game_id, match_no, court_no, state, created_at)
                            VALUES (:id, :gameId, :matchNo, :courtNo, :state, :createdAt)""")
                    .param("id", matchId)
                    .param("gameId", gameId)
                    .param("matchNo", game.nextMatchNo())
                    .param("courtNo", courtNo)
                    .param("state", MatchState.PLANNED.name())
                    .param("createdAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .update();
            jdbc.sql("UPDATE game SET next_match_no = next_match_no + 1 WHERE id = :gameId")
                    .param("gameId", gameId)
                    .update();
            insertPlayers(matchId, gameId, lineup.teamA(), "A");
            insertPlayers(matchId, gameId, lineup.teamB(), "B");
        } catch (DuplicateKeyException e) {
            // Never pretend the match was saved (section 15): the transaction rolls back and the host refreshes.
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }
        jdbc.sql("UPDATE attendance SET state = 'ASSIGNED' WHERE game_id = :gameId AND id IN (:ids)")
                .param("gameId", gameId)
                .param("ids", ids)
                .update();
        return new ConfirmMatchResponse(matchId, game.nextMatchNo(), courtNo, MatchState.PLANNED);
    }

    /** Reads and validates the raw body after the host check, so 401 / 404 / 403 come before 400. */
    private ConfirmMatchRequest parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        ConfirmMatchRequest request;
        try {
            request = mapper.readValue(body, ConfirmMatchRequest.class);
        } catch (JacksonException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return request;
    }

    private void insertPlayers(UUID matchId, UUID gameId, List<UUID> attendanceIds, String team) {
        for (UUID attendanceId : attendanceIds) {
            jdbc.sql("""
                            INSERT INTO match_player (match_id, game_id, attendance_id, team)
                            VALUES (:matchId, :gameId, :attendanceId, :team)""")
                    .param("matchId", matchId)
                    .param("gameId", gameId)
                    .param("attendanceId", attendanceId)
                    .param("team", team)
                    .update();
        }
    }

    private record GameRow(GameState state, int courtCount, MatchFormat format, int nextMatchNo) {}
}
