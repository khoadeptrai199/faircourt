package com.faircourt.player.level;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@code POST /internal/v1/players/levels} against PostgreSQL over real HTTP (Host board design 3.3; TC-12, TC-13). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class PlayerLevelsIT {

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

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/players/levels"));
        if (body != null) {
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.POST(HttpRequest.BodyPublishers.noBody());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String ids(Object... ids) {
        StringBuilder out = new StringBuilder("{\"playerIds\":[");
        for (int i = 0; i < ids.length; i++) {
            out.append(i == 0 ? "" : ",").append("\"").append(ids[i]).append("\"");
        }
        return out.append("]}").toString();
    }

    @Test
    void knownPlayersComeBackWithTheirLevelAndUnknownIdsAreLeftOut() throws Exception {
        UUID member = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO player_profile (id, user_id, kind, display_name, current_band, band_status)
				VALUES (:id, :user, 'MEMBER', 'Mem', 3, 'CONFIRMED')""").param("id", member).param("user", UUID.randomUUID()).update();
        jdbc.sql("""
				INSERT INTO player_profile (id, user_id, kind, display_name, current_band, band_status)
				VALUES (:id, NULL, 'GUEST', 'Gue', NULL, 'SELF_REPORTED')""").param("id", guest).update();

        HttpResponse<String> response = post(ids(member, guest, UUID.randomUUID()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(JsonAllowlist.fieldPaths(response.body()))
                .isEqualTo(Set.of(
                        "players", "players[].playerId", "players[].kind", "players[].band", "players[].bandStatus"));
        JsonNode players = JSON.readTree(response.body()).get("players");
        assertThat(players).hasSize(2);
        for (JsonNode p : players) {
            if (p.get("playerId").asString().equals(member.toString())) {
                assertThat(p.get("kind").asString()).isEqualTo("MEMBER");
                assertThat(p.get("band").asInt()).isEqualTo(3);
                assertThat(p.get("bandStatus").asString()).isEqualTo("CONFIRMED");
            } else {
                assertThat(p.get("playerId").asString()).isEqualTo(guest.toString());
                assertThat(p.get("kind").asString()).isEqualTo("GUEST");
                assertThat(p.get("band").isNull()).isTrue();
                assertThat(p.get("bandStatus").asString()).isEqualTo("SELF_REPORTED");
            }
        }
    }

    @Test
    void badBodiesAre400WithoutSqlText() throws Exception {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 201; i++) {
            many.append(i == 0 ? "" : ",")
                    .append("\"")
                    .append(UUID.randomUUID())
                    .append("\"");
        }
        for (String body : new String[] {
            "{\"playerIds\":[]}", "{\"playerIds\":[" + many + "]}", "{\"playerIds\":[\"not-a-uuid\"]}", null
        }) {
            HttpResponse<String> response = post(body);

            assertThat(response.statusCode()).as(String.valueOf(body)).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
            assertThat(response.body()).doesNotContainIgnoringCase("select").doesNotContainIgnoringCase("sql");
        }
    }
}
