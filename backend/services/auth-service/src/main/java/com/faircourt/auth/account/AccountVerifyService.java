package com.faircourt.auth.account;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Checks an email and password against {@code app_user} (blueprint sections 13, 15).
 *
 * <p>Account-enumeration rules (OWASP Authentication Cheat Sheet): an unknown email and a wrong password give the same
 * answer, and the same hashing work runs in both cases (a fixed dummy hash is matched when the email is unknown).
 * Hashes are compared by the password encoder, which compares in constant time.
 */
@Service
public class AccountVerifyService {

    /** BCrypt reads at most 72 bytes; longer passwords are refused instead of being silently cut (OWASP). */
    static final int MAX_PASSWORD_BYTES = 72;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AccountVerifyService(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        // Same encoder and cost as the real hashes, built once at startup from a random value nobody knows.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private record Row(UUID id, String email, String passwordHash) {}

    public AccountIdentityResponse verify(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        Optional<Row> account = jdbc.sql("""
						SELECT id, email, password_hash FROM app_user WHERE lower(email) = :email""")
                .param("email", email.strip().toLowerCase(Locale.ROOT))
                .query((rs, rowNum) ->
                        new Row(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")))
                .optional();

        boolean matches =
                passwordEncoder.matches(password, account.map(Row::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return new AccountIdentityResponse(account.get().id(), account.get().email());
    }
}
