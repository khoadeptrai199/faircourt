package com.faircourt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.LocalDemoSeeder;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL-backed checks for the V1 schema and the local demo seed (blueprint sections 0, 12, 17).
 * Runs against the dedicated faircourt_test database; refuses to clean any other database.
 */
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
class DatabaseSchemaIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @BeforeEach
    void resetTestDatabase() {
        String db = jdbc.sql("SELECT current_database()").query(String.class).single();
        assertThat(db).as("integration tests may only clean a *_test database").endsWith("_test");
        flyway.clean();
        flyway.migrate();
    }

    private LocalDemoSeeder seeder() {
        return new LocalDemoSeeder(jdbc, tx, passwordEncoder, clock, "host@faircourt.local", "test-only-password");
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void seedIsIdempotent() {
        LocalDemoSeeder.SeedResult first = seeder().seed();
        LocalDemoSeeder.SeedResult second = seeder().seed();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.publicCode()).isEqualTo(first.publicCode());
        assertThat(count("app_user")).isEqualTo(9);
        assertThat(count("player_profile")).isEqualTo(8);
        assertThat(count("play_session")).isEqualTo(1);
        assertThat(count("attendance")).isEqualTo(8);
    }

    @Test
    void seedCreatesOpenTwoCourtSessionWithEightReadyMembersOfVariedBands() {
        String code = seeder().seed().publicCode();

        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(jdbc.sql("SELECT court_count FROM play_session")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT state FROM play_session")
                        .query(String.class)
                        .single())
                .isEqualTo("OPEN");
        assertThat(jdbc.sql("SELECT DISTINCT state FROM attendance")
                        .query(String.class)
                        .list())
                .containsExactly("READY");
        assertThat(jdbc.sql("SELECT DISTINCT kind FROM player_profile")
                        .query(String.class)
                        .list())
                .containsExactly("MEMBER");
        assertThat(jdbc.sql("SELECT current_band FROM player_profile ORDER BY current_band")
                        .query(Integer.class)
                        .list())
                .containsExactly(1, 1, 2, 2, 3, 3, 4, 4);
        // Passwords are stored hashed, never in plain text.
        assertThat(jdbc.sql("SELECT count(*) FROM app_user WHERE password_hash = 'test-only-password'")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void databaseRejectsSecondBusyMatchOnSameCourt() {
        UUID session = sessionWithSeed();
        insertMatch(session, 1, 1, "PLANNED");

        assertThatThrownBy(() -> insertMatch(session, 2, 1, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A finished match does not keep the court busy.
        jdbc.sql("UPDATE play_match SET state = 'FINISHED' WHERE match_no = 1").update();
        insertMatch(session, 3, 1, "PLANNED");
    }

    @Test
    void databaseRejectsPlayerInTwoUnreleasedMatches() {
        UUID session = sessionWithSeed();
        UUID attendance = jdbc.sql("SELECT id FROM attendance ORDER BY id LIMIT 1")
                .query(UUID.class)
                .single();
        UUID match1 = insertMatch(session, 1, 1, "PLANNED");
        UUID match2 = insertMatch(session, 2, 2, "PLANNED");

        insertMatchPlayer(match1, session, attendance);
        assertThatThrownBy(() -> insertMatchPlayer(match2, session, attendance))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Once released, the player can be assigned again.
        jdbc.sql("UPDATE match_player SET released_at = now() WHERE match_id = :m")
                .param("m", match1)
                .update();
        insertMatchPlayer(match2, session, attendance);
    }

    private UUID sessionWithSeed() {
        seeder().seed();
        return jdbc.sql("SELECT id FROM play_session").query(UUID.class).single();
    }

    private UUID insertMatch(UUID session, int matchNo, int courtNo, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO play_match (id, session_id, match_no, court_no, state, created_at)
				VALUES (:id, :session, :no, :court, :state, :created)""")
                .param("id", id)
                .param("session", session)
                .param("no", matchNo)
                .param("court", courtNo)
                .param("state", state)
                .param("created", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
                .update();
        return id;
    }

    private void insertMatchPlayer(UUID match, UUID session, UUID attendance) {
        jdbc.sql("""
				INSERT INTO match_player (match_id, session_id, attendance_id, team)
				VALUES (:match, :session, :attendance, 'A')""")
                .param("match", match)
                .param("session", session)
                .param("attendance", attendance)
                .update();
    }
}
