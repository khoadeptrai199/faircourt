package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

/** {@code GET /api/v1/host/games} against PostgreSQL with a test identity token (Host board plan, TC-27 to TC-29). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class HostGamesIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final Instant BASE = Instant.parse("2026-11-25T10:00:00Z");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        TestDatabase.reset(jdbc, flyway);
    }

    private HttpResponse<String> mine(UUID user) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/host/games"));
        if (user != null) {
            request.header(
                    IdentityTokenHeader.NAME,
                    new IdentityToken(SECRET, Clock.systemUTC())
                            .issue(user, "playmatch-service", "GET", "/api/v1/host/games"));
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID insertGame(UUID host, String code, long startOffsetHours) {
        UUID id = UUID.randomUUID();
        Instant startsAt = BASE.plusSeconds(startOffsetHours * 3600);
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count)
				VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, 2)""")
                .param("id", id)
                .param("code", code)
                .param("host", host)
                .param("startsAt", OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(startsAt.plusSeconds(7200), ZoneOffset.UTC))
                .update();
        return id;
    }

    @Test
    void aHostSeesOnlyTheirOwnGamesNewestFirstWithExactlyTheDocumentedFields() throws Exception {
        UUID h = UUID.randomUUID();
        UUID older = insertGame(h, "OLDER001", 0);
        UUID newer = insertGame(h, "NEWER001", 24);
        insertGame(UUID.randomUUID(), "OTHER001", 48);

        HttpResponse<String> response = mine(h);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(JsonAllowlist.fieldPaths(response.body()))
                .isEqualTo(Set.of(
                        "games",
                        "games[].id",
                        "games[].publicCode",
                        "games[].title",
                        "games[].venue",
                        "games[].startsAt",
                        "games[].endsAt",
                        "games[].courtCount",
                        "games[].state"));
        JsonNode games = JSON.readTree(response.body()).get("games");
        List<String> ids = new ArrayList<>();
        for (JsonNode g : games) {
            ids.add(g.get("id").asString());
        }
        assertThat(ids).containsExactly(newer.toString(), older.toString());
    }

    @Test
    void anAccountThatHostsNothingGetsAnEmptyList() throws Exception {
        insertGame(UUID.randomUUID(), "OTHER001", 0);

        HttpResponse<String> response = mine(UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"games\":[]}");
    }

    @Test
    void atMostTheFiftyNewestGamesAreReturned() throws Exception {
        UUID h = UUID.randomUUID();
        for (int i = 0; i < 51; i++) {
            insertGame(h, String.format("G%07d", i), i);
        }

        JsonNode games = JSON.readTree(mine(h).body()).get("games");

        assertThat(games).hasSize(50);
        // The oldest game (offset 0, code G0000000) is the one left out.
        for (JsonNode g : games) {
            assertThat(g.get("publicCode").asString()).isNotEqualTo("G0000000");
        }
    }

    @Test
    void withoutAValidTokenTheAnswerIs401() throws Exception {
        assertThat(mine(null).statusCode()).isEqualTo(401);
    }
}
