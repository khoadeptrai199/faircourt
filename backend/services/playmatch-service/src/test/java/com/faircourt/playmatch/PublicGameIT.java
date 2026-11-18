package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/**
 * Public game page, backend (blueprint sections 3, 11, 13; OD-18…OD-20, OD-22): {@code GET
 * /api/v1/public/games/{publicCode}} against PostgreSQL over real HTTP. Same database guard and reset as
 * {@code @IntegrationTest}; declared directly because this test needs a running server (random port).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class PublicGameIT {

    /** Every field the public response may contain (AD-09). */
    static final String[] ALLOWED = {
        "gameId",
        "title",
        "venue",
        "startsAt",
        "endsAt",
        "courtCount",
        "state",
        "matchFormat",
        "minSets",
        "pricePerPerson",
        "pricePerPerson.male",
        "pricePerPerson.male.minVnd",
        "pricePerPerson.male.maxVnd",
        "pricePerPerson.female",
        "pricePerPerson.female.minVnd",
        "pricePerPerson.female.maxVnd"
    };

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID HOST = UUID.randomUUID();

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

    private HttpResponse<String> send(String method, String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send("GET", path);
    }

    private record Price(Integer maleMin, Integer maleMax, Integer femaleMin, Integer femaleMax) {
        static final Price NONE = new Price(null, null, null, null);
        static final Price DEMO = new Price(75000, 85000, 65000, 75000);
    }

    /** One game plus one attendance row whose name must never appear in the public response. */
    private UUID insertGame(String code, String state, String format, Price price) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count,
				  state, match_format, min_sets,
				  price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd)
				VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, 2,
				  :state, :format, 6, :maleMin, :maleMax, :femaleMin, :femaleMax)""")
                .param("id", id)
                .param("code", code)
                .param("host", HOST)
                .param("startsAt", OffsetDateTime.of(2026, 11, 17, 11, 0, 0, 0, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.of(2026, 11, 17, 14, 0, 0, 0, ZoneOffset.UTC))
                .param("state", state)
                .param("format", format)
                .param("maleMin", price.maleMin())
                .param("maleMax", price.maleMax())
                .param("femaleMin", price.femaleMin())
                .param("femaleMax", price.femaleMax())
                .update();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state)
				VALUES (:id, :game, :player, 'Secret Name', 'READY')""")
                .param("id", UUID.randomUUID())
                .param("game", id)
                .param("player", UUID.randomUUID())
                .update();
        return id;
    }

    private static void assertNotFound(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void anyoneReadsTheAllowlistedSummaryOfAGame() throws Exception {
        UUID id = insertGame("FRI7Q2K9", "OPEN", "DOUBLES", Price.DEMO);

        HttpResponse<String> response = get("/api/v1/public/games/FRI7Q2K9");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), ALLOWED);
        assertThat(response.body()).doesNotContain("Secret Name", HOST.toString(), "FRI7Q2K9");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("gameId").asString()).isEqualTo(id.toString());
        assertThat(body.get("title").asString()).isEqualTo("Friday Badminton");
        assertThat(body.get("venue").asString()).isEqualTo("Court House D7");
        assertThat(body.get("startsAt").asString()).isEqualTo("2026-11-17T11:00:00Z");
        assertThat(body.get("endsAt").asString()).isEqualTo("2026-11-17T14:00:00Z");
        assertThat(body.get("courtCount").asInt()).isEqualTo(2);
        assertThat(body.get("state").asString()).isEqualTo("OPEN");
        assertThat(body.get("matchFormat").asString()).isEqualTo("DOUBLES");
        assertThat(body.get("minSets").asInt()).isEqualTo(6);
        assertThat(body.get("pricePerPerson").toString())
                .isEqualTo("{\"male\":{\"minVnd\":75000,\"maxVnd\":85000},"
                        + "\"female\":{\"minVnd\":65000,\"maxVnd\":75000}}");
    }

    @Test
    void aLowercaseOrPaddedCodeFindsTheSameGame() throws Exception {
        insertGame("FRI7Q2K9", "OPEN", "SINGLES", Price.NONE);

        assertThat(get("/api/v1/public/games/fri7q2k9").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/public/games/%20FRI7Q2K9%20").statusCode()).isEqualTo(200);
    }

    @Test
    void unknownTooLongOrMalformedCodesAre404() throws Exception {
        insertGame("FRI7Q2K9", "OPEN", "DOUBLES", Price.NONE);

        assertNotFound(get("/api/v1/public/games/NOPE1234"));
        assertNotFound(get("/api/v1/public/games/" + "A".repeat(21)));
        assertNotFound(get("/api/v1/public/games/FRI-7Q2K9"));
        assertNotFound(get("/api/v1/public/games/%27%20OR%201=1"));
    }

    @Test
    void aClosedGameIsReturnedWithItsState() throws Exception {
        insertGame("CLOSED01", "CLOSED", "DOUBLES", Price.DEMO);

        HttpResponse<String> response = get("/api/v1/public/games/CLOSED01");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).get("state").asString()).isEqualTo("CLOSED");
    }

    @Test
    void priceIsNullWhenUnsetAndALineIsNullWhenOnlyTheOtherIsSet() throws Exception {
        insertGame("NOPRICE1", "OPEN", "DOUBLES", Price.NONE);
        insertGame("MENONLY1", "OPEN", "DOUBLES", new Price(80000, 80000, null, null));
        insertGame("ONEBOUND", "OPEN", "DOUBLES", new Price(null, null, 70000, null));

        JsonNode noPrice = JSON.readTree(get("/api/v1/public/games/NOPRICE1").body());
        JsonNode menOnly = JSON.readTree(get("/api/v1/public/games/MENONLY1").body());
        JsonNode oneBound = JSON.readTree(get("/api/v1/public/games/ONEBOUND").body());

        assertThat(noPrice.get("pricePerPerson").isNull()).isTrue();
        assertThat(menOnly.get("pricePerPerson").toString())
                .isEqualTo("{\"male\":{\"minVnd\":80000,\"maxVnd\":80000},\"female\":null}");
        assertThat(oneBound.get("pricePerPerson").toString())
                .isEqualTo("{\"male\":null,\"female\":{\"minVnd\":70000,\"maxVnd\":70000}}");
    }

    @Test
    void writesOnThePublicPathAreRefusedAndChangeNothing() throws Exception {
        insertGame("FRI7Q2K9", "OPEN", "DOUBLES", Price.NONE);

        for (String method : new String[] {"POST", "PUT", "DELETE"}) {
            HttpResponse<String> response = send(method, "/api/v1/public/games/FRI7Q2K9");

            assertThat(response.statusCode()).as(method).isEqualTo(401);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM game").query(Long.class).single())
                .isEqualTo(1);
    }
}
