package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.testsupport.IntegrationTest;
import com.faircourt.common.testsupport.TestDatabase;
import com.faircourt.playmatch.seed.LocalDemoSeeder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL-backed checks for the playmatch schema, its local demo seed and the DB backstops
 * (blueprint sections 0, 12, 17; F18 AC-02, AC-03, AC-04). Runs against the dedicated faircourt_test database;
 * refuses to clean any other database. Flyway cleans only the "playmatch" schema.
 */
@IntegrationTest
class PlaymatchSchemaIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    Clock clock;

    @BeforeEach
    void resetTestSchema() {
        TestDatabase.reset(jdbc, flyway);
    }

    private LocalDemoSeeder seeder() {
        return new LocalDemoSeeder(jdbc, tx, clock);
    }

    private static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void migrationCreatesOnlyThePlaymatchTablesInThePlaymatchSchema() {
        assertThat(jdbc.sql("SELECT current_schema()").query(String.class).single())
                .isEqualTo("playmatch");
        assertThat(jdbc.sql("""
						SELECT table_name FROM information_schema.tables
						WHERE table_schema = 'playmatch' ORDER BY table_name""").query(String.class).list())
                .containsExactly("attendance", "flyway_schema_history", "game", "match_player", "play_match");
    }

    @Test
    void noTableColumnConstraintOrIndexIsNamedAfterSessionOrGamesPlayed() {
        String names = """
				SELECT table_name AS name FROM information_schema.tables WHERE table_schema = 'playmatch'
				UNION ALL
				SELECT column_name FROM information_schema.columns WHERE table_schema = 'playmatch'
				UNION ALL
				SELECT con.conname FROM pg_constraint con
				  JOIN pg_namespace nsp ON nsp.oid = con.connamespace WHERE nsp.nspname = 'playmatch'
				UNION ALL
				SELECT indexname FROM pg_indexes WHERE schemaname = 'playmatch'""";

        assertThat(jdbc.sql(names).query(String.class).list())
                .contains("game_id", "matches_played", "game_pkey", "ix_game_host_start")
                .noneMatch(name -> name.contains("session") || name.contains("games_played"));
    }

    @Test
    void seedIsIdempotent() {
        LocalDemoSeeder.SeedResult first = seeder().seed();
        LocalDemoSeeder.SeedResult second = seeder().seed();

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.publicCode()).isEqualTo(first.publicCode());
        assertThat(count("game")).isEqualTo(1);
        assertThat(count("attendance")).isEqualTo(8);
    }

    @Test
    void seedCreatesOpenTwoCourtDoublesGameWithMinimumSetsAndPrice() {
        String code = seeder().seed().publicCode();

        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");
        Map<String, Object> game = jdbc.sql("""
						SELECT id, host_user_id, court_count, state, match_format, min_sets,
						  price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd
						FROM game""").query().singleRow();
        assertThat(game)
                .containsEntry("id", demoId("session:1"))
                .containsEntry("host_user_id", demoId("user:host"))
                .containsEntry("court_count", 2)
                .containsEntry("state", "OPEN")
                .containsEntry("match_format", "DOUBLES")
                .containsEntry("min_sets", 6)
                .containsEntry("price_male_min_vnd", 75000)
                .containsEntry("price_male_max_vnd", 85000)
                .containsEntry("price_female_min_vnd", 65000)
                .containsEntry("price_female_max_vnd", 75000);
    }

    @Test
    void seedCreatesEightReadyAttendancesLinkedToTheDemoPlayerProfiles() {
        seeder().seed();

        assertThat(jdbc.sql("SELECT DISTINCT state FROM attendance")
                        .query(String.class)
                        .list())
                .containsExactly("READY");
        assertThat(jdbc.sql("SELECT player_id FROM attendance")
                        .query(UUID.class)
                        .list())
                .containsExactlyInAnyOrder(
                        demoId("player:1"),
                        demoId("player:2"),
                        demoId("player:3"),
                        demoId("player:4"),
                        demoId("player:5"),
                        demoId("player:6"),
                        demoId("player:7"),
                        demoId("player:8"));
        assertThat(jdbc.sql("SELECT display_name FROM attendance WHERE player_id = :p")
                        .param("p", demoId("player:3"))
                        .query(String.class)
                        .single())
                .isEqualTo("Player 3");
    }

    @Test
    void databaseRejectsAMinimumSetsOutsideOneToTwenty() {
        seeder().seed();

        assertThatThrownBy(() -> jdbc.sql("UPDATE game SET min_sets = 21").update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE game SET min_sets = 0").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsAPriceRangeWhoseMinimumIsAboveItsMaximum() {
        seeder().seed();

        assertThatThrownBy(() ->
                        jdbc.sql("UPDATE game SET price_male_min_vnd = 90000").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSecondBusyMatchOnSameCourt() {
        UUID game = gameWithSeed();
        insertMatch(game, 1, 1, "PLANNED");

        assertThatThrownBy(() -> insertMatch(game, 2, 1, "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);

        // A finished match does not keep the court busy.
        jdbc.sql("UPDATE play_match SET state = 'FINISHED' WHERE match_no = 1").update();
        insertMatch(game, 3, 1, "PLANNED");
    }

    @Test
    void databaseRejectsPlayerInTwoUnreleasedMatches() {
        UUID game = gameWithSeed();
        UUID attendance = jdbc.sql("SELECT id FROM attendance ORDER BY id LIMIT 1")
                .query(UUID.class)
                .single();
        UUID match1 = insertMatch(game, 1, 1, "PLANNED");
        UUID match2 = insertMatch(game, 2, 2, "PLANNED");

        insertMatchPlayer(match1, game, attendance);
        assertThatThrownBy(() -> insertMatchPlayer(match2, game, attendance))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Once released, the player can be assigned again.
        jdbc.sql("UPDATE match_player SET released_at = now() WHERE match_id = :m")
                .param("m", match1)
                .update();
        insertMatchPlayer(match2, game, attendance);
    }

    private UUID gameWithSeed() {
        seeder().seed();
        return jdbc.sql("SELECT id FROM game").query(UUID.class).single();
    }

    private UUID insertMatch(UUID game, int matchNo, int courtNo, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO play_match (id, game_id, match_no, court_no, state, created_at)
				VALUES (:id, :game, :no, :court, :state, :created)""")
                .param("id", id)
                .param("game", game)
                .param("no", matchNo)
                .param("court", courtNo)
                .param("state", state)
                .param("created", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
                .update();
        return id;
    }

    private void insertMatchPlayer(UUID match, UUID game, UUID attendance) {
        jdbc.sql("""
				INSERT INTO match_player (match_id, game_id, attendance_id, team)
				VALUES (:match, :game, :attendance, 'A')""")
                .param("match", match)
                .param("game", game)
                .param("attendance", attendance)
                .update();
    }
}
