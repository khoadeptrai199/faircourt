package com.faircourt.playmatch.attendance;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import com.faircourt.playmatch.game.GameState;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's manual attendance changes: approve a pending guest, mark a player as left (blueprint sections 11, 13;
 * Attendance management design 4, 5). One local transaction: the game row is locked {@code FOR SHARE} (a later close
 * waits), the attendance row {@code FOR UPDATE} (two taps and the later match confirm run one after the other). Never
 * touches {@code available_since} or {@code matches_played}. No call to another service, so nothing to compensate.
 */
@Service
public class AttendanceService {

    private final JdbcClient jdbc;
    private final AccessPolicy policy;

    public AttendanceService(JdbcClient jdbc, AccessPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional
    public HostAttendanceResponse changeState(
            CallerIdentity caller, UUID gameId, UUID attendanceId, AttendanceState target) {
        policy.requireHost(caller, gameId);
        if (!target.isHostTarget()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        GameState game = jdbc.sql("SELECT state FROM game WHERE id = :gameId FOR SHARE")
                .param("gameId", gameId)
                .query((rs, rowNum) -> GameState.valueOf(rs.getString("state")))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (game == GameState.CLOSED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "This game is closed.");
        }
        Row row = jdbc.sql("""
                        SELECT state, available_since FROM attendance
                        WHERE id = :attendanceId AND game_id = :gameId FOR UPDATE""")
                .param("attendanceId", attendanceId)
                .param("gameId", gameId)
                .query((rs, rowNum) -> new Row(
                        AttendanceState.valueOf(rs.getString("state")),
                        rs.getTimestamp("available_since").toInstant()))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if (row.state() == target) {
            // Same state as now (double tap, two tabs): nothing to write (design D-2).
            return new HostAttendanceResponse(attendanceId, row.state(), row.availableSince());
        }
        if (row.state().inMatch()) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "Cancel or finish this player's match first.");
        }
        if (!row.state().hostMayMoveTo(target)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        jdbc.sql("UPDATE attendance SET state = :target WHERE id = :attendanceId")
                .param("target", target.name())
                .param("attendanceId", attendanceId)
                .update();
        return new HostAttendanceResponse(attendanceId, target, row.availableSince());
    }

    private record Row(AttendanceState state, Instant availableSince) {}
}
