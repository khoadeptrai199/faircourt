package com.faircourt.playmatch.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.attendance.AttendanceState;
import com.faircourt.playmatch.game.GameState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guest check-in orchestration (Guest check-in design section 4; AD-16): the first write that spans three services.
 * Steps: check the game, return an existing live attendance of the same browser, create the profile (player-service),
 * insert the {@code PENDING} attendance (one local transaction), issue the credential (auth-service). If the last step
 * fails the attendance is set to {@code LEFT} (compensation) and the caller gets 503. Remote calls run outside any
 * database transaction; writes are never retried. No name, answer or token is logged.
 */
@Service
public class GuestCheckInService {

    private static final Logger log = LoggerFactory.getLogger(GuestCheckInService.class);

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,20}");

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PlayerClient playerClient;
    private final AuthClient authClient;
    private final Duration cookieGrace;

    public GuestCheckInService(
            JdbcClient jdbc,
            TransactionTemplate tx,
            Clock clock,
            PlayerClient playerClient,
            AuthClient authClient,
            @Value("${faircourt.guest.cookie-grace}") Duration cookieGrace) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.playerClient = playerClient;
        this.authClient = authClient;
        this.cookieGrace = cookieGrace;
    }

    private record GameRow(UUID id, GameState state, Instant endsAt) {}

    public GuestCheckInResponse checkIn(String publicCode, GuestCheckInRequest request) {
        String name = cleanName(request.displayName());
        GameRow game = findGame(publicCode);
        if (game.state() == GameState.CLOSED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "This game is closed.");
        }

        // The same browser again: a live attendance of this game is returned without any write.
        if (request.existingAttendanceId() != null) {
            Optional<AttendanceState> existing = liveAttendanceState(request.existingAttendanceId(), game.id());
            if (existing.isPresent()) {
                return new GuestCheckInResponse(existing.get(), false, null);
            }
        }

        UUID playerId = playerClient.createGuest(name, game.id(), request.answers());

        UUID attendanceId = UUID.randomUUID();
        try {
            tx.executeWithoutResult(status -> jdbc.sql("""
							INSERT INTO attendance (id, game_id, player_id, display_name, state)
							VALUES (:id, :game, :player, :name, 'PENDING')""")
                    .param("id", attendanceId)
                    .param("game", game.id())
                    .param("player", playerId)
                    .param("name", name)
                    .update());
        } catch (RuntimeException e) {
            // A guest profile with no attendance stays behind (design section 4); it holds nothing but the typed name.
            log.warn(
                    "Guest check-in: attendance insert failed after the profile was created: {}",
                    e.getClass().getSimpleName());
            throw e;
        }

        Instant now = clock.instant();
        Instant expiresAt = (game.endsAt().isAfter(now) ? game.endsAt() : now).plus(cookieGrace);
        GuestCredential credential;
        try {
            credential = authClient.issue(game.id(), attendanceId, expiresAt);
        } catch (RuntimeException e) {
            compensate(attendanceId);
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return new GuestCheckInResponse(AttendanceState.PENDING, true, credential);
    }

    /** Compensation: the guest has no credential, so the entry is withdrawn (state LEFT). */
    private void compensate(UUID attendanceId) {
        try {
            tx.executeWithoutResult(
                    status -> jdbc.sql("UPDATE attendance SET state = 'LEFT' WHERE id = :id AND state = 'PENDING'")
                            .param("id", attendanceId)
                            .update());
        } catch (RuntimeException e) {
            // The guest stays PENDING without a cookie; the host can reject the entry (design section 4).
            log.error("Guest check-in: compensation failed, an attendance stays PENDING without a credential");
        }
    }

    private GameRow findGame(String publicCode) {
        String code = publicCode == null ? "" : publicCode.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return jdbc.sql("SELECT id, state, ends_at FROM game WHERE public_code = :code")
                .param("code", code)
                .query((rs, rowNum) -> new GameRow(
                        rs.getObject("id", UUID.class),
                        GameState.valueOf(rs.getString("state")),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant()))
                .optional()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private Optional<AttendanceState> liveAttendanceState(UUID attendanceId, UUID gameId) {
        return jdbc.sql("SELECT state FROM attendance WHERE id = :id AND game_id = :game AND state <> 'LEFT'")
                .param("id", attendanceId)
                .param("game", gameId)
                .query((rs, rowNum) -> AttendanceState.valueOf(rs.getString("state")))
                .optional();
    }

    /**
     * Trimmed, 1-80 characters, no control characters (design D-9). Same rule as GuestController.validName in the
     * gateway and GuestProfileService.cleanName in player-service: keep the three identical.
     */
    static String cleanName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return name;
    }
}
