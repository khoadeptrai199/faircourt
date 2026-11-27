package com.faircourt.playmatch.attendance;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.guest.PlayerClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Member join and own-attendance read (My attendance design sections 4, 5; AD-16). Join: check the game, ask
 * player-service for the member's profile (created when missing), then one local transaction inserts the {@code READY}
 * attendance. The remote call runs outside any database transaction and is not retried; if it fails nothing is
 * written. No name or email is logged.
 */
@Service
public class MemberAttendanceService {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PlayerClient playerClient;
    private final MyAttendanceQuery myAttendanceQuery;

    public MemberAttendanceService(
            JdbcClient jdbc, TransactionTemplate tx, PlayerClient playerClient, MyAttendanceQuery myAttendanceQuery) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.playerClient = playerClient;
        this.myAttendanceQuery = myAttendanceQuery;
    }

    public MemberAttendanceResponse join(UUID gameId, UUID userId, String displayName) {
        String name = cleanName(displayName);
        GameState state = jdbc.sql("SELECT state FROM game WHERE id = :id")
                .param("id", gameId)
                .query((rs, rowNum) -> GameState.valueOf(rs.getString("state")))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (state == GameState.CLOSED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "This game is closed.");
        }

        // The profile's own name wins; the name from the account is used only when the profile is created now.
        PlayerClient.MemberProfile profile = playerClient.findOrCreateMember(userId, name);
        UUID playerId = profile.playerId();
        String attendanceName = profile.displayName();

        // Two parallel joins: the unique (game_id, player_id) lets one insert win; the other inserts nothing and reads
        // the winner's row.
        MemberAttendanceResponse result = tx.execute(status -> {
            // The game may have closed while player-service was called: check again under a shared row lock, so a
            // concurrent close waits until this insert is committed and a closed game gets no row.
            GameState locked = jdbc.sql("SELECT state FROM game WHERE id = :id FOR SHARE")
                    .param("id", gameId)
                    .query((rs, rowNum) -> GameState.valueOf(rs.getString("state")))
                    .optional()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
            if (locked == GameState.CLOSED) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "This game is closed.");
            }
            int inserted = jdbc.sql("""
							INSERT INTO attendance (id, game_id, player_id, display_name, state)
							VALUES (:id, :game, :player, :name, 'READY')
							ON CONFLICT (game_id, player_id) DO NOTHING""")
                    .param("id", UUID.randomUUID())
                    .param("game", gameId)
                    .param("player", playerId)
                    .param("name", attendanceName)
                    .update();
            AttendanceState current = myAttendanceQuery
                    .byPlayer(gameId, playerId)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR))
                    .state();
            return new MemberAttendanceResponse(current, inserted == 1);
        });
        if (result.attendanceState() == AttendanceState.LEFT) {
            // The host removed this player; a member cannot silently re-enter (design D-4).
            throw new ApiException(ErrorCode.STATE_CONFLICT, "You were removed from this game.");
        }
        return result;
    }

    /**
     * The caller's own state in this game: exactly one of {@code userId} (a signed-in member) or {@code attendanceId}
     * (a guest). 404 when the caller has no row in this game, with no hint whether the game or the person is unknown.
     */
    public MyAttendanceResponse myAttendance(UUID gameId, UUID userId, UUID attendanceId) {
        Optional<MyAttendanceQuery.MyAttendanceRow> row;
        if (attendanceId != null) {
            row = myAttendanceQuery.byAttendance(gameId, attendanceId);
        } else {
            row = playerClient.findMember(userId).flatMap(playerId -> myAttendanceQuery.byPlayer(gameId, playerId));
        }
        MyAttendanceQuery.MyAttendanceRow own = row.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new MyAttendanceResponse(own.state(), own.assignment());
    }

    /** Same name rule as GuestCheckInService.cleanName: trimmed, 1-80 characters, no control characters. */
    static String cleanName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty()
                || name.codePointCount(0, name.length()) > 80
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return name;
    }
}
