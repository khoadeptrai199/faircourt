package com.faircourt.auth.seed;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local-only, idempotent demo seed for the auth schema (blueprint section 0; owner decision 2026-11-09;
 * F18 design section 4).
 *
 * Creates, only if the demo host account does not exist yet: the host account and 8 member pilot accounts
 * (player1..player8@faircourt.local), all with the demo password, stored hashed. IDs are derived exactly as in
 * player-service and playmatch-service ({@link #demoId}). Runs in one transaction.
 */
@Component
@Profile("local")
public class LocalDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    static final int PLAYER_COUNT = 8;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final String hostEmail;
    private final String password;

    public LocalDemoSeeder(
            JdbcClient jdbc,
            TransactionTemplate tx,
            PasswordEncoder passwordEncoder,
            @Value("${faircourt.seed.host-email}") String hostEmail,
            @Value("${faircourt.seed.password}") String password) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.hostEmail = hostEmail;
        this.password = password;
    }

    /** Same derivation in every service: {@code UUID.nameUUIDFromBytes("faircourt-demo:<kind>:<n>")}. */
    static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = seed();
        log.info(
                "FairCourt local demo seed (auth) {} | host login: {}",
                created ? "CREATED" : "already present",
                hostEmail);
    }

    /** Returns true when the demo accounts were created now, false when they already existed. */
    public boolean seed() {
        return Boolean.TRUE.equals(tx.execute(status -> {
            boolean exists = jdbc.sql("SELECT count(*) FROM app_user WHERE lower(email) = lower(:email)")
                            .param("email", hostEmail)
                            .query(Long.class)
                            .single()
                    > 0;
            if (exists) {
                return false;
            }
            String hash = passwordEncoder.encode(password);
            insertUser(demoId("user:host"), hostEmail, hash);
            for (int i = 1; i <= PLAYER_COUNT; i++) {
                insertUser(demoId("user:" + i), "player" + i + "@faircourt.local", hash);
            }
            return true;
        }));
    }

    private void insertUser(UUID id, String email, String hash) {
        jdbc.sql("INSERT INTO app_user (id, email, password_hash) VALUES (:id, :email, :hash)")
                .param("id", id)
                .param("email", email)
                .param("hash", hash)
                .update();
    }
}
