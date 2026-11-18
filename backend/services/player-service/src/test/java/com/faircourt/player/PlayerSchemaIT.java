package com.faircourt.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.IntegrationTest;
import com.faircourt.common.testsupport.TestDatabase;
import com.faircourt.player.seed.LocalDemoSeeder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL-backed checks for the player schema and its local demo seed (blueprint sections 0, 12; F18 AC-02,
 * AC-03). Runs against the dedicated faircourt_test database; Flyway cleans only the "player" schema.
 */
@IntegrationTest
class PlayerSchemaIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void resetTestSchema() {
        TestDatabase.reset(jdbc, flyway);
    }

    private LocalDemoSeeder seeder() {
        return new LocalDemoSeeder(jdbc, tx);
    }

    private static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void migrationCreatesOnlyThePlayerTablesInThePlayerSchema() {
        assertThat(jdbc.sql("SELECT current_schema()").query(String.class).single())
                .isEqualTo("player");
        assertThat(jdbc.sql("""
						SELECT table_name FROM information_schema.tables
						WHERE table_schema = 'player' ORDER BY table_name""").query(String.class).list())
                .containsExactly("flyway_schema_history", "player_profile", "skill_assessment");
    }

    @Test
    void skillAssessmentReferencesItsGameByGameId() {
        assertThat(jdbc.sql("""
						SELECT column_name FROM information_schema.columns
						WHERE table_schema = 'player' AND table_name = 'skill_assessment'""").query(String.class).list()).contains("game_id").doesNotContain("session_id");
    }

    @Test
    void seedIsIdempotent() {
        boolean first = seeder().seed();
        boolean second = seeder().seed();

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM player_profile")
                        .query(Long.class)
                        .single())
                .isEqualTo(8);
    }

    @Test
    void seedCreatesEightMembersOfVariedBandsLinkedToTheirAccounts() {
        seeder().seed();

        assertThat(jdbc.sql("SELECT DISTINCT kind FROM player_profile")
                        .query(String.class)
                        .list())
                .containsExactly("MEMBER");
        assertThat(jdbc.sql("SELECT current_band FROM player_profile ORDER BY current_band")
                        .query(Integer.class)
                        .list())
                .containsExactly(1, 1, 2, 2, 3, 3, 4, 4);
        assertThat(jdbc.sql("SELECT user_id FROM player_profile WHERE id = :id")
                        .param("id", demoId("player:5"))
                        .query(UUID.class)
                        .single())
                .isEqualTo(demoId("user:5"));
    }
}
