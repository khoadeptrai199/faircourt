package com.faircourt.playmatch.attendance;

import com.faircourt.playmatch.match.MatchState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read projection for "my game" (AD-02): selects only the {@code state} of one row and, through the caller's own
 * unreleased {@code match_player} row, that match's number, court and state; and only if the row belongs to the given
 * game. One statement, so state and assignment come from the same snapshot. The caller's identity (a player id or an
 * attendance id) was resolved by the gateway; a row of another game or another person is simply not found. No other
 * {@code match_player} row, team, name or band is read.
 */
@Component
public class MyAttendanceQuery {

    /** The caller's state and optional own assignment. */
    public record MyAttendanceRow(AttendanceState state, MyAssignment assignment) {}

    private static final String SELECT = """
            SELECT a.state, m.match_no, m.court_no, m.state AS match_state
            FROM attendance a
            LEFT JOIN match_player mp
                   ON mp.attendance_id = a.id AND mp.game_id = a.game_id AND mp.released_at IS NULL
            LEFT JOIN play_match m ON m.id = mp.match_id AND m.game_id = a.game_id
            """;

    private final JdbcClient jdbc;

    public MyAttendanceQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A guest's own row: the attendance id came from a valid guest cookie. */
    public Optional<MyAttendanceRow> byAttendance(UUID gameId, UUID attendanceId) {
        return jdbc.sql(SELECT + "WHERE a.id = :attendance AND a.game_id = :game")
                .param("attendance", attendanceId)
                .param("game", gameId)
                .query(MyAttendanceQuery::row)
                .optional();
    }

    /** A member's own row: the player id is the profile of the signed-in account. */
    public Optional<MyAttendanceRow> byPlayer(UUID gameId, UUID playerId) {
        return jdbc.sql(SELECT + "WHERE a.player_id = :player AND a.game_id = :game")
                .param("player", playerId)
                .param("game", gameId)
                .query(MyAttendanceQuery::row)
                .optional();
    }

    private static MyAttendanceRow row(ResultSet rs, int rowNum) throws SQLException {
        AttendanceState state = AttendanceState.valueOf(rs.getString("state"));
        String matchState = rs.getString("match_state");
        MyAssignment assignment = matchState == null
                ? null
                : new MyAssignment(rs.getInt("match_no"), rs.getInt("court_no"), MatchState.valueOf(matchState));
        return new MyAttendanceRow(state, assignment);
    }
}
