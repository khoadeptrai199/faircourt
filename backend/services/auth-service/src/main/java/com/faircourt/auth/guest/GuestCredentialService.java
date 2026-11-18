package com.faircourt.auth.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest credentials (blueprint section 12; OWASP Session Management Cheat Sheet): 32 random bytes from
 * {@link SecureRandom}, handed out once as base64url, stored only as a SHA-256 hash. The raw token exists only in the
 * answer of {@link #issue}; it is never logged and never written to the database. One active credential per
 * attendance (the unique index {@code ux_active_guest_access}): issuing a new one revokes the older one.
 */
@Service
public class GuestCredentialService {

    /** 32 bytes in base64url without padding. */
    private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GuestCredentialService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A freshly issued credential: the raw token (only here) and when it ends. */
    public record Issued(String token, Instant expiresAt) {}

    /** Who a valid token belongs to. */
    public record Resolved(UUID gameId, UUID attendanceId) {}

    private record Row(UUID gameId, UUID attendanceId, String tokenHash) {}

    @Transactional
    public Issued issue(UUID gameId, UUID attendanceId, Instant expiresAt) {
        Instant now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        jdbc.sql("UPDATE guest_access SET revoked_at = :now WHERE attendance_id = :attendance AND revoked_at IS NULL")
                .param("now", at(now))
                .param("attendance", attendanceId)
                .update();
        jdbc.sql("""
						INSERT INTO guest_access (id, game_id, attendance_id, token_hash, expires_at, issued_at)
						VALUES (:id, :game, :attendance, :hash, :expiresAt, :now)""")
                .param("id", UUID.randomUUID())
                .param("game", gameId)
                .param("attendance", attendanceId)
                .param("hash", hash(token))
                .param("expiresAt", at(expiresAt))
                .param("now", at(now))
                .update();
        return new Issued(token, expiresAt);
    }

    /** The owner of a token that is known, not expired and not revoked; empty for every other case (one answer). */
    public Optional<Resolved> resolve(String token) {
        if (token == null || !TOKEN_SHAPE.matcher(token).matches()) {
            return Optional.empty();
        }
        String hash = hash(token);
        return jdbc.sql("""
						SELECT game_id, attendance_id, token_hash FROM guest_access
						WHERE token_hash = :hash AND revoked_at IS NULL AND expires_at > :now""")
                .param("hash", hash)
                .param("now", at(clock.instant()))
                .query((rs, rowNum) -> new Row(
                        rs.getObject("game_id", UUID.class),
                        rs.getObject("attendance_id", UUID.class),
                        rs.getString("token_hash")))
                .optional()
                .filter(row -> MessageDigest.isEqual(
                        row.tokenHash().getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII)))
                .map(row -> new Resolved(row.gameId(), row.attendanceId()));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** SHA-256 of the token as 64 lowercase hex characters (the value stored in {@code token_hash}). */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
