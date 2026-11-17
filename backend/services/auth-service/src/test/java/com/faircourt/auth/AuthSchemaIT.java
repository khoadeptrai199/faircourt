package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.auth.seed.LocalDemoSeeder;
import com.faircourt.common.testsupport.IntegrationTest;
import com.faircourt.common.testsupport.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL-backed checks for the auth schema and its local demo seed (blueprint sections 0, 12; F18 AC-02,
 * AC-03). Runs against the dedicated faircourt_test database; Flyway cleans only the "auth" schema.
 */
@IntegrationTest
class AuthSchemaIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetTestSchema() {
        TestDatabase.reset(jdbc, flyway);
    }

    private LocalDemoSeeder seeder() {
        return new LocalDemoSeeder(jdbc, tx, passwordEncoder, "host@faircourt.local", "test-only-password");
    }

    private static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void migrationCreatesOnlyTheAuthTablesInTheAuthSchema() {
        assertThat(jdbc.sql("SELECT current_schema()").query(String.class).single())
                .isEqualTo("auth");
        assertThat(jdbc.sql("""
						SELECT table_name FROM information_schema.tables
						WHERE table_schema = 'auth' ORDER BY table_name""").query(String.class).list())
                .containsExactly("app_user", "flyway_schema_history", "guest_access");
    }

    @Test
    void seedIsIdempotent() {
        boolean first = seeder().seed();
        boolean second = seeder().seed();

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM app_user").query(Long.class).single())
                .isEqualTo(9);
    }

    @Test
    void seedCreatesHostAndEightMemberAccountsWithTheSharedDemoIds() {
        seeder().seed();

        assertThat(jdbc.sql("SELECT id FROM app_user WHERE email = 'host@faircourt.local'")
                        .query(UUID.class)
                        .single())
                .isEqualTo(demoId("user:host"));
        assertThat(jdbc.sql("SELECT id FROM app_user WHERE email = 'player8@faircourt.local'")
                        .query(UUID.class)
                        .single())
                .isEqualTo(demoId("user:8"));
        // Passwords are stored hashed, never in plain text.
        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE password_hash = 'test-only-password'")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
