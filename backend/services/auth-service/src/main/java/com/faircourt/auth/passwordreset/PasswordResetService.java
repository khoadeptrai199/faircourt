package com.faircourt.auth.passwordreset;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Password reset by an emailed one-time code (blueprint section 15; owner approved 2026-11-18; OWASP Forgot Password
 * Cheat Sheet).
 *
 * <ul>
 *   <li>Code: 8 random digits from {@link SecureRandom}, valid 10 minutes, one use, 5 wrong tries, stored only as a
 *       BCrypt hash. A new code cancels the older ones.
 *   <li>Asking for a code: 1 per 60 seconds and 3 per hour per email, counted in the database. Over the limit, for an
 *       unknown email, or on success the caller sees the same thing; the same hashing work runs in every case.
 *   <li>Resetting: every kind of bad code gives the same result (false). The account is never locked.
 *   <li>Mails are sent after the commit, on another thread ({@link ResetMailSender}).
 * </ul>
 */
@Service
public class PasswordResetService {

    static final int CODE_DIGITS = 8;
    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final Duration MIN_GAP = Duration.ofSeconds(60);
    static final Duration HOUR = Duration.ofHours(1);
    static final int MAX_CODES_PER_HOUR = 3;
    static final int MAX_WRONG_TRIES = 5;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ResetMailSender mail;
    private final SecureRandom random = new SecureRandom();
    /** Same encoder and cost as the real hashes, from a value nobody knows: used when there is nothing real to compare. */
    private final String dummyHash;

    public PasswordResetService(
            JdbcClient jdbc,
            PasswordEncoder passwordEncoder,
            TransactionTemplate tx,
            Clock clock,
            ResetMailSender mail) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.clock = clock;
        this.mail = mail;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private record Account(UUID id, String email) {}

    private record ResetRow(UUID id, String codeHash, OffsetDateTime expiresAt, int failedAttempts) {}

    private Optional<Account> findAccount(String email) {
        return jdbc.sql("SELECT id, email FROM app_user WHERE lower(email) = :email")
                .param("email", email.strip().toLowerCase(Locale.ROOT))
                .query((rs, rowNum) -> new Account(rs.getObject("id", UUID.class), rs.getString("email")))
                .optional();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Sends a code when allowed; returns nothing, so the caller cannot tell the cases apart. */
    public void requestCode(String email) {
        String code = String.format("%0" + CODE_DIGITS + "d", random.nextInt((int) Math.pow(10, CODE_DIGITS)));
        // Always hash, also for an unknown or throttled email, so every case costs about the same time.
        String codeHash = passwordEncoder.encode(code);
        Optional<Account> account = findAccount(email);
        if (account.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(status -> {
            OffsetDateTime now = now();
            UUID userId = account.get().id();
            // Lock the account row so two parallel requests cannot both pass the limit.
            jdbc.sql("SELECT id FROM app_user WHERE id = :id FOR UPDATE")
                    .param("id", userId)
                    .query(UUID.class)
                    .single();
            int lastHour = jdbc.sql("SELECT count(*) FROM password_reset WHERE user_id = :u AND created_at > :since")
                    .param("u", userId)
                    .param("since", now.minus(HOUR))
                    .query(Integer.class)
                    .single();
            boolean tooSoon = jdbc.sql("SELECT count(*) FROM password_reset WHERE user_id = :u AND created_at > :since")
                            .param("u", userId)
                            .param("since", now.minus(MIN_GAP))
                            .query(Integer.class)
                            .single()
                    > 0;
            if (lastHour >= MAX_CODES_PER_HOUR || tooSoon) {
                return;
            }
            jdbc.sql("UPDATE password_reset SET consumed_at = :now WHERE user_id = :u AND consumed_at IS NULL")
                    .param("now", now)
                    .param("u", userId)
                    .update();
            jdbc.sql("""
							INSERT INTO password_reset (id, user_id, code_hash, created_at, expires_at)
							VALUES (:id, :u, :hash, :created, :expires)""")
                    .param("id", UUID.randomUUID())
                    .param("u", userId)
                    .param("hash", codeHash)
                    .param("created", now)
                    .param("expires", now.plus(CODE_LIFETIME))
                    .update();
            afterCommit(() -> mail.sendCode(account.get().email(), code));
        });
    }

    /** True when the password was changed. False for every kind of bad code (wrong, expired, used, replaced, unknown). */
    public boolean reset(String email, String code, String newPassword) {
        PasswordRules.validate(email, newPassword);
        // The same two hashing steps (new password, code) run for every request, known account or not, so the time says
        // nothing about the account. They run outside any lock, so a flood of requests cannot hold database locks.
        String newHash = passwordEncoder.encode(newPassword);
        Optional<Account> account = findAccount(email);
        Optional<ResetRow> candidate = account.flatMap(a -> newestOpenCode(a.id()));
        boolean matches =
                passwordEncoder.matches(code, candidate.map(ResetRow::codeHash).orElse(dummyHash));
        if (account.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        Boolean changed = tx.execute(status -> {
            OffsetDateTime now = now();
            UUID userId = account.get().id();
            // Lock the code row. It may have been used up, cancelled or replaced while the code was compared.
            Optional<ResetRow> row = lockOpenCode(candidate.get().id());
            if (row.isEmpty()) {
                return false;
            }
            if (!row.get().expiresAt().isAfter(now)) {
                consume(row.get().id(), now);
                return false;
            }
            if (!matches) {
                int tries = jdbc.sql(
                                "UPDATE password_reset SET failed_attempts = failed_attempts + 1 WHERE id = :id RETURNING failed_attempts")
                        .param("id", row.get().id())
                        .query(Integer.class)
                        .single();
                if (tries >= MAX_WRONG_TRIES) {
                    consume(row.get().id(), now);
                }
                return false;
            }
            jdbc.sql("UPDATE app_user SET password_hash = :hash, password_changed_at = :now WHERE id = :id")
                    .param("hash", newHash)
                    .param("now", now)
                    .param("id", userId)
                    .update();
            jdbc.sql("UPDATE password_reset SET consumed_at = :now WHERE user_id = :u AND consumed_at IS NULL")
                    .param("now", now)
                    .param("u", userId)
                    .update();
            afterCommit(() -> mail.sendPasswordChanged(account.get().email(), now.toInstant()));
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    /** The newest code of the account that is not used up or cancelled (read without a lock). */
    private Optional<ResetRow> newestOpenCode(UUID userId) {
        return jdbc.sql("""
						SELECT id, code_hash, expires_at, failed_attempts FROM password_reset
						WHERE user_id = :u AND consumed_at IS NULL
						ORDER BY created_at DESC LIMIT 1""")
                .param("u", userId)
                .query(PasswordResetService::toRow)
                .optional();
    }

    private Optional<ResetRow> lockOpenCode(UUID id) {
        return jdbc.sql("""
						SELECT id, code_hash, expires_at, failed_attempts FROM password_reset
						WHERE id = :id AND consumed_at IS NULL FOR UPDATE""").param("id", id).query(PasswordResetService::toRow).optional();
    }

    private static ResetRow toRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ResetRow(
                rs.getObject("id", UUID.class),
                rs.getString("code_hash"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getInt("failed_attempts"));
    }

    private void consume(UUID id, OffsetDateTime now) {
        jdbc.sql("UPDATE password_reset SET consumed_at = :now WHERE id = :id")
                .param("now", now)
                .param("id", id)
                .update();
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
