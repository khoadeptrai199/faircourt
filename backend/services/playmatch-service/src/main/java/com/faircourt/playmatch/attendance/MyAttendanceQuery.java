package com.faircourt.playmatch.attendance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read projection for "my game" (AD-02): selects only {@code state} of one row, and only if the row belongs to the
 * given game. The caller's identity (a player id or an attendance id) was resolved by the gateway; a row of another
 * game or another person is simply not found.
 */
@Component
public class MyAttendanceQuery {

    private final JdbcClient jdbc;

    public MyAttendanceQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A guest's own row: the attendance id came from a valid guest cookie. */
    public Optional<AttendanceState> byAttendance(UUID gameId, UUID attendanceId) {
        return jdbc.sql("SELECT state FROM attendance WHERE id = :attendance AND game_id = :game")
                .param("attendance", attendanceId)
                .param("game", gameId)
                .query((rs, rowNum) -> AttendanceState.valueOf(rs.getString("state")))
                .optional();
    }

    /** A member's own row: the player id is the profile of the signed-in account. */
    public Optional<AttendanceState> byPlayer(UUID gameId, UUID playerId) {
        return jdbc.sql("SELECT state FROM attendance WHERE player_id = :player AND game_id = :game")
                .param("player", playerId)
                .param("game", gameId)
                .query((rs, rowNum) -> AttendanceState.valueOf(rs.getString("state")))
                .optional();
    }
}
