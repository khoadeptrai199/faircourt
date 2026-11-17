package com.faircourt.player.seed;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local-only, idempotent demo seed for the player schema (blueprint section 0; owner decision 2026-11-09;
 * F18 design section 4).
 *
 * Creates, only if the first demo profile does not exist yet: 8 MEMBER profiles "Player 1".."Player 8" with
 * bands 1,1,2,2,3,3,4,4, each linked to its auth-service account. IDs are derived exactly as in auth-service and
 * playmatch-service ({@link #demoId}). Runs in one transaction.
 */
@Component
@Profile("local")
public class LocalDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    static final int PLAYER_COUNT = 8;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public LocalDemoSeeder(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** Same derivation in every service: {@code UUID.nameUUIDFromBytes("faircourt-demo:<kind>:<n>")}. */
    static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean created = seed();
        log.info("FairCourt local demo seed (player) {}", created ? "CREATED" : "already present");
    }

    /** Returns true when the demo profiles were created now, false when they already existed. */
    public boolean seed() {
        return Boolean.TRUE.equals(tx.execute(status -> {
            boolean exists = jdbc.sql("SELECT count(*) FROM player_profile WHERE id = :id")
                            .param("id", demoId("player:1"))
                            .query(Long.class)
                            .single()
                    > 0;
            if (exists) {
                return false;
            }
            for (int i = 1; i <= PLAYER_COUNT; i++) {
                jdbc.sql("""
						INSERT INTO player_profile (id, user_id, kind, display_name, current_band)
						VALUES (:id, :user, 'MEMBER', :name, :band)""")
                        .param("id", demoId("player:" + i))
                        .param("user", demoId("user:" + i))
                        .param("name", "Player " + i)
                        .param("band", (i + 1) / 2)
                        .update();
            }
            return true;
        }));
    }
}
