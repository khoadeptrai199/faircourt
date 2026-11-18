package com.faircourt.player.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guest check-in, player-service side (blueprint section 13; design section 4): {@code POST
 * /internal/v1/players/guests} against PostgreSQL over real HTTP.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class GuestProfileIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID GAME = UUID.randomUUID();

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

    private HttpResponse<String> create(String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/players/guests"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String body(String name, String answers) {
        return "{\"displayName\":\"" + name + "\",\"gameId\":\"" + GAME + "\",\"answers\":" + answers + "}";
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    @Test
    void validAnswersCreateOneGuestProfileAndOneSelfAssessmentWithTheMappedBand() throws Exception {
        HttpResponse<String> response =
                create(body("  Mai  ", "{\"serve\":2,\"rally\":1,\"movement\":1,\"positioning\":1}"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonAllowlist.assertOnlyFields(response.body(), "playerId");
        UUID playerId =
                UUID.fromString(JSON.readTree(response.body()).get("playerId").asString());
        Map<String, Object> profile = jdbc.sql("SELECT * FROM player_profile WHERE id = :id")
                .param("id", playerId)
                .query()
                .singleRow();
        assertThat(profile)
                .containsEntry("kind", "GUEST")
                .containsEntry("display_name", "Mai")
                .containsEntry("band_status", "SELF_REPORTED")
                .containsEntry("user_id", null);
        assertThat(((Number) profile.get("current_band")).intValue()).isEqualTo(2);
        Map<String, Object> assessment = jdbc.sql("SELECT source, band, game_id, answers::text AS answers, "
                        + "reviewer_user_id FROM skill_assessment WHERE player_id = :id")
                .param("id", playerId)
                .query()
                .singleRow();
        assertThat(assessment.get("source")).isEqualTo("SELF");
        assertThat(((Number) assessment.get("band")).intValue()).isEqualTo(2);
        assertThat(assessment.get("game_id")).isEqualTo(GAME);
        assertThat(assessment.get("reviewer_user_id")).isNull();
        assertThat(JSON.readTree((String) assessment.get("answers"))
                        .get("serve")
                        .asInt())
                .isEqualTo(2);
        assertThat(count("player_profile")).isEqualTo(1);
        assertThat(count("skill_assessment")).isEqualTo(1);
    }

    @Test
    void unknownAnswersGiveAProfileWithoutABand() throws Exception {
        assertThat(create(body("Mai", "null")).statusCode()).isEqualTo(201);
        assertThat(create(body("Linh", "{\"serve\":null,\"rally\":null,\"movement\":null,\"positioning\":null}"))
                        .statusCode())
                .isEqualTo(201);

        assertThat(count("player_profile")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM player_profile WHERE current_band IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM skill_assessment WHERE band IS NULL AND answers IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void sameNameTwiceIsAllowedAndMakesTwoProfiles() throws Exception {
        assertThat(create(body("Mai", "null")).statusCode()).isEqualTo(201);
        assertThat(create(body("Mai", "null")).statusCode()).isEqualTo(201);

        assertThat(count("player_profile")).isEqualTo(2);
    }

    @Test
    void aNameOfEightyCharactersIsAcceptedAndAPaddedNameIsTrimmed() throws Exception {
        assertThat(create(body("A".repeat(80), "null")).statusCode()).isEqualTo(201);
        assertThat(create(body("   Mai   ", "null")).statusCode()).isEqualTo(201);

        assertThat(jdbc.sql("SELECT count(*) FROM player_profile WHERE display_name = 'Mai'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void partialOutOfRangeOrBadNameIs400AndWritesNothing() throws Exception {
        String[] bodies = {
            body("Mai", "{\"serve\":1,\"rally\":1,\"movement\":1,\"positioning\":null}"),
            body("Mai", "{\"serve\":4,\"rally\":1,\"movement\":1,\"positioning\":1}"),
            body("Mai", "{\"serve\":-1,\"rally\":1,\"movement\":1,\"positioning\":1}"),
            // Name vectors: keep identical to the gateway and playmatch-service tests.
            body("", "null"),
            body("   ", "null"),
            body("A".repeat(81), "null"),
            body("Ma\\u0007i", "null"),
            body("Ma\\u0009i", "null"),
            "{}",
            "{not json"
        };
        for (String json : bodies) {
            HttpResponse<String> response = create(json);

            assertThat(response.statusCode()).as(json).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        }
        assertThat(count("player_profile")).isZero();
        assertThat(count("skill_assessment")).isZero();
    }
}
