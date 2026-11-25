package com.faircourt.player.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Member profile routes, player-service side (My attendance design sections 3 and 4): {@code POST
 * /internal/v1/players/members} finds or creates the {@code MEMBER} profile of an account, {@code GET
 * /internal/v1/players/by-user/{userId}} finds it. Against PostgreSQL over real HTTP.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class MemberProfileIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetTestSchema() {
        TestDatabase.reset(jdbc, flyway);
    }

    private HttpResponse<String> create(UUID userId, String name) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/players/members"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"userId\":\"" + userId + "\",\"displayName\":\"" + name + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> byUser(String userId) throws Exception {
        return http.send(
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/internal/v1/players/by-user/" + userId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static UUID playerIdOf(HttpResponse<String> response) {
        return UUID.fromString(JSON.readTree(response.body()).get("playerId").asString());
    }

    private long profileCount() {
        return jdbc.sql("SELECT count(*) FROM player_profile").query(Long.class).single();
    }

    @Test
    void anAccountWithoutAProfileGetsOneMemberProfileWithNoBandAndTheSameIdTheSecondTime() throws Exception {
        UUID userId = UUID.randomUUID();

        HttpResponse<String> first = create(userId, "  host  ");
        HttpResponse<String> second = create(userId, "host");

        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(first.body(), "playerId", "displayName");
        assertThat(JSON.readTree(first.body()).get("displayName").asString()).isEqualTo("host");
        assertThat(playerIdOf(second)).isEqualTo(playerIdOf(first));
        assertThat(profileCount()).isEqualTo(1);
        Map<String, Object> profile = jdbc.sql("SELECT * FROM player_profile WHERE id = :id")
                .param("id", playerIdOf(first))
                .query()
                .singleRow();
        assertThat(profile)
                .containsEntry("kind", "MEMBER")
                .containsEntry("user_id", userId)
                .containsEntry("display_name", "host")
                .containsEntry("band_status", "SELF_REPORTED")
                .containsEntry("current_band", null);
        assertThat(jdbc.sql("SELECT count(*) FROM skill_assessment")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void anExistingProfileKeepsItsNameAndTheAnswerReturnsIt() throws Exception {
        UUID userId = UUID.randomUUID();
        create(userId, "Player 3");

        HttpResponse<String> again = create(userId, "email-name");

        assertThat(again.statusCode()).as(again.body()).isEqualTo(200);
        assertThat(JSON.readTree(again.body()).get("displayName").asString()).isEqualTo("Player 3");
        assertThat(jdbc.sql("SELECT display_name FROM player_profile")
                        .query(String.class)
                        .single())
                .isEqualTo("Player 3");
    }

    @Test
    void aNameOfEightyCodePointsMadeOfSurrogatePairsIsAccepted() throws Exception {
        String name = "😀".repeat(80);

        HttpResponse<String> response = create(UUID.randomUUID(), name);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("displayName").asString()).isEqualTo(name);
        assertThat(create(UUID.randomUUID(), name + "😀").statusCode()).isEqualTo(400);
    }

    @Test
    void parallelCallsForTheSameAccountMakeOneProfileAndReturnTheSameId() throws Exception {
        UUID userId = UUID.randomUUID();
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<UUID>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                calls.add(() -> playerIdOf(create(userId, "host")));
            }

            List<UUID> ids = new ArrayList<>();
            for (Future<UUID> future : pool.invokeAll(calls)) {
                ids.add(future.get());
            }

            assertThat(profileCount()).isEqualTo(1);
            assertThat(ids).hasSize(callers).containsOnly(ids.getFirst());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anExistingProfileOfThisAccountIsFoundAndAnUnknownAccountIs404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID playerId = playerIdOf(create(userId, "host"));

        HttpResponse<String> found = byUser(userId.toString());
        HttpResponse<String> unknown = byUser(UUID.randomUUID().toString());
        HttpResponse<String> malformed = byUser("not-a-uuid");

        assertThat(found.statusCode()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(found.body(), "playerId");
        assertThat(playerIdOf(found)).isEqualTo(playerId);
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(malformed.statusCode()).isEqualTo(404);
    }

    @Test
    void aGuestProfileIsNeverReturnedForAnAccountLookup() throws Exception {
        // A GUEST row has user_id NULL, so no account id can ever match it.
        jdbc.sql("""
				INSERT INTO player_profile (id, user_id, kind, display_name, band_status)
				VALUES (:id, NULL, 'GUEST', 'Mai', 'SELF_REPORTED')""").param("id", UUID.randomUUID()).update();

        assertThat(byUser(UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void badNamesAndMissingFieldsAre400AndWriteNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        // Name vectors: keep identical to the guest and playmatch-service tests.
        for (String name : new String[] {"", "   ", "A".repeat(81), "Ma\\u0007i", "Ma\\u0009i"}) {
            HttpResponse<String> response = create(userId, name);

            assertThat(response.statusCode()).as(name).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        }
        for (String json : new String[] {"{}", "{not json", "{\"displayName\":\"host\"}"}) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/players/members"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).as(json).isEqualTo(400);
        }
        assertThat(profileCount()).isZero();
    }
}
