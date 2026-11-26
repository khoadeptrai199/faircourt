package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import com.faircourt.playmatch.game.CreateGameRequest;
import com.faircourt.playmatch.game.CreateGameResponse;
import com.faircourt.playmatch.game.GameService;
import com.faircourt.playmatch.game.PublicCodeGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /api/v1/games} against PostgreSQL with a test identity token (Create game plan, TC-01 to TC-08, TC-10,
 * TC-13, TC-14). TC-13 and TC-14 call GameService directly with a scripted code generator.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class CreateGameIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final String SERVICE = "playmatch-service";
    private static final String PATH = "/api/v1/games";
    private static final String CODE_PATTERN = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}";

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccessPolicy policy;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID host = UUID.randomUUID();

    @BeforeEach
    void reset() {
        TestDatabase.reset(jdbc, flyway);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static String token(UUID user, String method, String path) {
        return new IdentityToken(SECRET, Clock.systemUTC()).issue(user, SERVICE, method, path);
    }

    private static String iso(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.ofHours(7)).toString();
    }

    private static ObjectNode body(Instant startsAt, Instant endsAt) {
        ObjectNode body = JSON.createObjectNode();
        body.put("title", "Friday after-work doubles");
        body.put("venue", "Lan Anh Club, District 10");
        body.put("startsAt", iso(startsAt));
        body.put("endsAt", iso(endsAt));
        body.put("courtCount", 2);
        body.put("matchFormat", "DOUBLES");
        body.put("minSets", 6);
        return body;
    }

    private static ObjectNode validBody() {
        Instant start = Instant.now().plus(Duration.ofHours(1));
        return body(start, start.plus(Duration.ofHours(3)));
    }

    private static ObjectNode withPrice(ObjectNode body) {
        ObjectNode price = body.putObject("pricePerPerson");
        price.putObject("male").put("minVnd", 75000).put("maxVnd", 85000);
        price.putObject("female").put("minVnd", 65000).put("maxVnd", 75000);
        return body;
    }

    private HttpResponse<String> post(String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> create(ObjectNode body) throws Exception {
        return post(token(host, "POST", PATH), JSON.writeValueAsString(body));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private int gameCount() {
        return jdbc.sql("SELECT count(*) FROM game").query(Integer.class).single();
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM game").query().listOfRows();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private static Instant instant(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    // ---- TC-01 to TC-05 -----------------------------------------------------------------------------------------

    @Test
    void aValidRequestCreatesTheGameForTheCallerAndAnswersWithOnlyIdAndCode() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant end = start.plus(Duration.ofHours(3));
        ObjectNode body = withPrice(body(start, end));
        body.put("title", "  Friday after-work doubles ");
        body.put("venue", " Lan Anh Club, District 10  ");

        HttpResponse<String> response = create(body);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode json = JSON.readTree(response.body());
        JsonAllowlist.assertOnlyFields(response.body(), "gameId", "publicCode");
        UUID gameId = UUID.fromString(json.get("gameId").asString());
        String code = json.get("publicCode").asString();
        assertThat(code).matches(CODE_PATTERN);
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/host/games/" + gameId + "/board");
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        Map<String, Object> row = onlyRow();
        assertThat(row.get("id")).isEqualTo(gameId);
        assertThat(row.get("public_code")).isEqualTo(code);
        assertThat(row.get("host_user_id")).isEqualTo(host);
        assertThat(row.get("state")).isEqualTo("OPEN");
        assertThat(row.get("next_match_no")).isEqualTo(1);
        assertThat(row.get("title")).isEqualTo("Friday after-work doubles");
        assertThat(row.get("venue")).isEqualTo("Lan Anh Club, District 10");
        assertThat(instant(row.get("starts_at"))).isEqualTo(start);
        assertThat(instant(row.get("ends_at"))).isEqualTo(end);
        assertThat(row.get("court_count")).isEqualTo(2);
        assertThat(row.get("match_format")).isEqualTo("DOUBLES");
        assertThat(row.get("min_sets")).isEqualTo(6);
        assertThat(row.get("price_male_min_vnd")).isEqualTo(75000);
        assertThat(row.get("price_male_max_vnd")).isEqualTo(85000);
        assertThat(row.get("price_female_min_vnd")).isEqualTo(65000);
        assertThat(row.get("price_female_max_vnd")).isEqualTo(75000);
    }

    @Test
    void aRequestWithoutPriceStoresNoPriceAndThePublicPageShowsNull() throws Exception {
        HttpResponse<String> response = create(validBody());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        Map<String, Object> row = onlyRow();
        assertThat(row.get("price_male_min_vnd")).isNull();
        assertThat(row.get("price_male_max_vnd")).isNull();
        assertThat(row.get("price_female_min_vnd")).isNull();
        assertThat(row.get("price_female_max_vnd")).isNull();
        String code = JSON.readTree(response.body()).get("publicCode").asString();
        HttpResponse<String> page = get("/api/v1/public/games/" + code, null);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(page.body()).get("pricePerPerson").isNull()).isTrue();
    }

    @Test
    void aPriceLineWithOnlyOneBoundIsStoredAsMinEqualsMax() throws Exception {
        ObjectNode body = validBody();
        body.putObject("pricePerPerson").putObject("male").put("minVnd", 80000);

        HttpResponse<String> response = create(body);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        Map<String, Object> row = onlyRow();
        assertThat(row.get("price_male_min_vnd")).isEqualTo(80000);
        assertThat(row.get("price_male_max_vnd")).isEqualTo(80000);
        assertThat(row.get("price_female_min_vnd")).isNull();
        assertThat(row.get("price_female_max_vnd")).isNull();
    }

    @Test
    void aHostUserIdInTheBodyIsIgnoredAndTheCallerIsTheHost() throws Exception {
        UUID player1 = UUID.randomUUID();
        ObjectNode body = validBody();
        body.put("hostUserId", player1.toString());

        HttpResponse<String> response = create(body);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(onlyRow().get("host_user_id")).isEqualTo(host).isNotEqualTo(player1);
    }

    @Test
    void aGameThatEndsAfterMidnightIsStoredOnTheNextDay() throws Exception {
        java.time.ZoneId vietnam = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.LocalDate tomorrow = java.time.LocalDate.now(vietnam).plusDays(1);
        Instant start = tomorrow.atTime(20, 0).atZone(vietnam).toInstant();
        Instant end = tomorrow.plusDays(1).atTime(0, 30).atZone(vietnam).toInstant();

        HttpResponse<String> response = create(body(start, end));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        Map<String, Object> row = onlyRow();
        assertThat(instant(row.get("ends_at"))).isEqualTo(end);
        assertThat(instant(row.get("ends_at")).atZone(vietnam).toLocalDate()).isEqualTo(tomorrow.plusDays(1));
        assertThat(instant(row.get("starts_at")).atZone(vietnam).toLocalDate()).isEqualTo(tomorrow);
    }

    // ---- TC-06 --------------------------------------------------------------------------------------------------

    @Test
    void theCreatedGameIsOnTheHostListAndBoardAndPublicPageButNotOpenToAnotherAccount() throws Exception {
        ObjectNode body = validBody();
        HttpResponse<String> response = create(body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode json = JSON.readTree(response.body());
        String gameId = json.get("gameId").asString();
        String code = json.get("publicCode").asString();

        String list = "/api/v1/host/games";
        String board = "/api/v1/host/games/" + gameId + "/board";
        HttpResponse<String> mine = get(list, token(host, "GET", list));
        HttpResponse<String> hostBoard = get(board, token(host, "GET", board));
        HttpResponse<String> otherBoard = get(board, token(UUID.randomUUID(), "GET", board));
        HttpResponse<String> page = get("/api/v1/public/games/" + code, null);

        assertThat(mine.statusCode()).isEqualTo(200);
        assertThat(mine.body()).contains(gameId);
        assertThat(hostBoard.statusCode()).as(hostBoard.body()).isEqualTo(200);
        assertThat(otherBoard.statusCode()).isEqualTo(403);
        assertThat(otherBoard.body()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(page.body()).get("title").asString())
                .isEqualTo(body.get("title").asString());
    }

    // ---- TC-07, TC-08 -------------------------------------------------------------------------------------------

    static Stream<Arguments> invalidBodies() {
        Instant now = Instant.now();
        return Stream.of(
                invalid("blank title", b -> b.put("title", "   ")),
                invalid("title 121 chars after trim", b -> b.put("title", " " + "a".repeat(121) + " ")),
                invalid("blank venue", b -> b.put("venue", " ")),
                invalid("venue 161 chars", b -> b.put("venue", "v".repeat(161))),
                invalid("missing startsAt", b -> b.remove("startsAt")),
                invalid("startsAt without offset", b -> b.put("startsAt", "2031-01-01T18:00:00")),
                invalid(
                        "endsAt equals startsAt",
                        b -> b.put("endsAt", b.get("startsAt").asString())),
                invalid("endsAt before startsAt", b -> b.put("endsAt", iso(now.plus(Duration.ofMinutes(30))))),
                invalid("game already ended", b -> {
                    b.put("startsAt", iso(now.minus(Duration.ofHours(3))));
                    b.put("endsAt", iso(now.minus(Duration.ofHours(1))));
                }),
                invalid("length 12 h 1 min", b -> {
                    b.put("startsAt", iso(now.plus(Duration.ofHours(1))));
                    b.put("endsAt", iso(now.plus(Duration.ofHours(13)).plus(Duration.ofMinutes(1))));
                }),
                invalid("courtCount 0", b -> b.put("courtCount", 0)),
                invalid("courtCount 5", b -> b.put("courtCount", 5)),
                invalid("matchFormat MIXED", b -> b.put("matchFormat", "MIXED")),
                invalid("minSets 0", b -> b.put("minSets", 0)),
                invalid("minSets 21", b -> b.put("minSets", 21)),
                invalid(
                        "price -1",
                        b -> b.putObject("pricePerPerson").putObject("male").put("minVnd", -1)),
                invalid(
                        "price 10,000,001",
                        b -> b.putObject("pricePerPerson").putObject("female").put("maxVnd", 10_000_001)),
                invalid(
                        "minVnd above maxVnd",
                        b -> b.putObject("pricePerPerson")
                                .putObject("male")
                                .put("minVnd", 90000)
                                .put("maxVnd", 80000)));
    }

    private static Arguments invalid(String name, Consumer<ObjectNode> change) {
        return Arguments.of(name, change);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void anInvalidFieldIs400AndWritesNoRow(String name, Consumer<ObjectNode> change) throws Exception {
        ObjectNode body = validBody();
        change.accept(body);

        HttpResponse<String> response = create(body);

        assertThat(response.statusCode()).as(name + ": " + response.body()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(gameCount()).isZero();
    }

    @Test
    void aBodyThatIsNotJsonIs400AndWritesNoRow() throws Exception {
        HttpResponse<String> response = post(token(host, "POST", PATH), "this is not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(gameCount()).isZero();
    }

    @Test
    void aGameLastingExactlyTwelveHoursIsAccepted() throws Exception {
        Instant start = Instant.now().plus(Duration.ofHours(1));

        HttpResponse<String> response = create(body(start, start.plus(Duration.ofHours(12))));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    @Test
    void aGameThatEndsOneMinuteFromNowIsAcceptedEvenIfItStartedAlready() throws Exception {
        Instant now = Instant.now();

        HttpResponse<String> response = create(body(now.minus(Duration.ofHours(2)), now.plus(Duration.ofMinutes(1))));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    // ---- TC-10 --------------------------------------------------------------------------------------------------

    @Test
    void aTokenIssuedForAnotherRouteIsRefusedAndWritesNoRow() throws Exception {
        String joinPath = "/api/v1/games/" + UUID.randomUUID() + "/join";
        String forThisBody = JSON.writeValueAsString(validBody());

        HttpResponse<String> response = post(token(host, "POST", joinPath), forThisBody);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(gameCount()).isZero();
    }

    // ---- TC-13, TC-14 -------------------------------------------------------------------------------------------

    private void insertGameWithCode(String code) {
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count)
				VALUES (:id, :code, :host, 'Taken', 'Court House D7', :startsAt, :endsAt, 2)""")
                .param("id", UUID.randomUUID())
                .param("code", code)
                .param("host", UUID.randomUUID())
                .param("startsAt", OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .param("endsAt", OffsetDateTime.now(ZoneOffset.UTC).plusHours(3))
                .update();
    }

    /** A generator that returns the scripted codes in order and repeats the last one. */
    private static PublicCodeGenerator scripted(String... codes) {
        return new PublicCodeGenerator() {
            private int next;

            @Override
            public String next() {
                return codes[Math.min(next++, codes.length - 1)];
            }
        };
    }

    private GameService serviceWith(PublicCodeGenerator generator) {
        return new GameService(jdbc, policy, generator, Clock.systemUTC());
    }

    private CreateGameRequest request() throws Exception {
        return JSON.readValue(JSON.writeValueAsString(validBody()), CreateGameRequest.class);
    }

    @Test
    void aCodeCollisionIsRetriedWithTheNextCode() throws Exception {
        insertGameWithCode("TAKEN001");
        GameService service = serviceWith(scripted("TAKEN001", "TAKEN001", "NEWCODE1"));

        CreateGameResponse created = service.create(new CallerIdentity.Account(host), request());

        assertThat(created.publicCode()).isEqualTo("NEWCODE1");
        assertThat(gameCount()).isEqualTo(2);
    }

    @Test
    void fiveCollisionsGiveInternalErrorAndWriteNoRow() throws Exception {
        insertGameWithCode("TAKEN001");
        GameService service = serviceWith(scripted("TAKEN001"));

        assertThatThrownBy(() -> service.create(new CallerIdentity.Account(host), request()))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(gameCount()).isEqualTo(1);
    }
}
