package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/v1/host/games/{gameId}/matches} against PostgreSQL with a test identity token (Match confirm plan,
 * TC-05 to TC-19). player-service is a JDK HTTP stub, used only where the board and suggestions are read (TC-18).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchConfirmIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final String SERVICE = "playmatch-service";
    private static final Instant T = Instant.parse("2026-11-26T12:30:00Z");
    private static final Instant BASE = Instant.parse("2026-11-26T10:00:00Z");
    private static final String CONFLICT_MESSAGE = "A player or court is no longer available.";

    /** The application's clock, set by the test; tokens and {@code created_at} follow it. */
    static final class TestClock extends Clock {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return T;
        }
    }

    private static final TestClock CLOCK = new TestClock();

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    /** Stand-in for player-service: answers a level for every requested player. */
    private static final Map<UUID, Boolean> KNOWN = new ConcurrentHashMap<>();

    private static final HttpServer PLAYER = startPlayerStub();

    private static HttpServer startPlayerStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", MatchConfirmIT::answerPlayer);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answerPlayer(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        StringBuilder body = new StringBuilder("{\"players\":[");
        boolean first = true;
        for (JsonNode id : JSON.readTree(request).get("playerIds")) {
            body.append(first ? "" : ",")
                    .append("{\"playerId\":\"")
                    .append(id.asString())
                    .append("\",\"kind\":\"MEMBER\",\"band\":2,\"bandStatus\":\"CONFIRMED\"}");
            first = false;
        }
        body.append("]}");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @DynamicPropertySource
    static void playerServiceIsTheStub(DynamicPropertyRegistry registry) {
        registry.add(
                "faircourt.services.player-url",
                () -> "http://127.0.0.1:" + PLAYER.getAddress().getPort());
    }

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID host = UUID.randomUUID();

    @BeforeEach
    void reset() {
        TestDatabase.reset(jdbc, flyway);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static String matchesPath(Object gameId) {
        return "/api/v1/host/games/" + gameId + "/matches";
    }

    private static String token(UUID user, String method, String path) {
        return new IdentityToken(SECRET, CLOCK).issue(user, SERVICE, method, path);
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header(IdentityTokenHeader.NAME, token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String body(int court, List<UUID> a, List<UUID> b) {
        return "{\"courtNo\":" + court + ",\"teams\":{\"A\":" + idList(a) + ",\"B\":" + idList(b) + "}}";
    }

    private static String idList(List<UUID> ids) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : ids) {
            quoted.add("\"" + id + "\"");
        }
        return "[" + String.join(",", quoted) + "]";
    }

    private HttpResponse<String> hostConfirm(UUID game, String json) throws Exception {
        return post(matchesPath(game), token(host, "POST", matchesPath(game)), json);
    }

    private HttpResponse<String> hostConfirm(UUID game, int court, List<UUID> a, List<UUID> b) throws Exception {
        return hostConfirm(game, body(court, a, b));
    }

    private UUID insertGame(int courts, String format, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
					INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state,
					                  match_format, min_sets)
					VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, :courts, :state,
					        :format, 6)""")
                .param("id", id)
                .param("code", id.toString().substring(0, 8).toUpperCase())
                .param("host", host)
                .param("startsAt", OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(BASE.plusSeconds(7200), ZoneOffset.UTC))
                .param("courts", courts)
                .param("state", state)
                .param("format", format)
                .update();
        return id;
    }

    private UUID attend(UUID game, String state) {
        UUID id = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        KNOWN.put(player, true);
        jdbc.sql("""
					INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
					                        matches_played)
					VALUES (:id, :game, :player, :name, :state, :checkedIn, :available, 2)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("name", "Zorbulon " + id.toString().substring(0, 4))
                .param("state", state)
                .param("checkedIn", OffsetDateTime.ofInstant(BASE.minusSeconds(60), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(BASE.plusSeconds(300), ZoneOffset.UTC))
                .update();
        return id;
    }

    private List<UUID> readyPlayers(UUID game, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(attend(game, "READY"));
        }
        return ids;
    }

    private UUID insertMatch(UUID game, int matchNo, int court, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO play_match (id, game_id, match_no, court_no, state) VALUES (:id, :game, :no, :court, :s)")
                .param("id", id)
                .param("game", game)
                .param("no", matchNo)
                .param("court", court)
                .param("s", state)
                .update();
        return id;
    }

    private void matchPlayer(UUID match, UUID game, UUID attendance, String team) {
        jdbc.sql("INSERT INTO match_player (match_id, game_id, attendance_id, team) VALUES (:m, :g, :a, :t)")
                .param("m", match)
                .param("g", game)
                .param("a", attendance)
                .param("t", team)
                .update();
    }

    /** Everything a failed confirm must leave alone. */
    private record Snapshot(long matches, long matchPlayers, List<String> attendance, List<Integer> nextMatchNo) {}

    private Snapshot snapshot() {
        return new Snapshot(
                jdbc.sql("SELECT count(*) FROM play_match").query(Long.class).single(),
                jdbc.sql("SELECT count(*) FROM match_player").query(Long.class).single(),
                jdbc.sql("SELECT id || '|' || state || '|' || available_since || '|' || matches_played"
                                + " FROM attendance ORDER BY id")
                        .query(String.class)
                        .list(),
                jdbc.sql("SELECT next_match_no FROM game ORDER BY id")
                        .query(Integer.class)
                        .list());
    }

    private String stateOf(UUID attendance) {
        return jdbc.sql("SELECT state FROM attendance WHERE id = :id")
                .param("id", attendance)
                .query(String.class)
                .single();
    }

    private int nextMatchNo(UUID game) {
        return jdbc.sql("SELECT next_match_no FROM game WHERE id = :id")
                .param("id", game)
                .query(Integer.class)
                .single();
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response).get("code").asString()).isEqualTo(code);
    }

    private static void assertMatchConflict(HttpResponse<String> response) {
        assertCode(response, 409, "MATCH_CONFLICT");
        assertThat(json(response).get("message").asString()).isEqualTo(CONFLICT_MESSAGE);
    }

    // ---- TC-05, TC-06 ----------------------------------------------------------------------------------------

    @Test
    void confirmingFourReadyPlayersSavesOnePlannedMatchAndAssignsThemAndNothingElseChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        List<String> before = jdbc.sql(
                        "SELECT id || '|' || available_since || '|' || matches_played FROM attendance" + " ORDER BY id")
                .query(String.class)
                .list();

        HttpResponse<String> response = hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode result = json(response);
        UUID matchId = UUID.fromString(result.get("matchId").asString());
        assertThat(result.get("matchNo").asInt()).isEqualTo(1);
        assertThat(result.get("courtNo").asInt()).isEqualTo(1);
        assertThat(result.get("state").asString()).isEqualTo("PLANNED");
        JsonAllowlist.assertOnlyFields(response.body(), "matchId", "matchNo", "courtNo", "state");
        assertThat(response.body())
                .isEqualTo("{\"matchId\":\"" + matchId + "\",\"matchNo\":1,\"courtNo\":1,\"state\":\"PLANNED\"}");
        Map<String, Object> match = jdbc.sql(
                        "SELECT state, court_no, match_no, created_at FROM play_match WHERE id = :id")
                .param("id", matchId)
                .query((rs, n) -> Map.<String, Object>of(
                        "state", rs.getString("state"),
                        "court", rs.getInt("court_no"),
                        "no", rs.getInt("match_no"),
                        "createdAt", rs.getTimestamp("created_at").toInstant()))
                .single();
        assertThat(match)
                .containsEntry("state", "PLANNED")
                .containsEntry("court", 1)
                .containsEntry("no", 1);
        assertThat(match).containsEntry("createdAt", T);
        assertThat(jdbc.sql("SELECT count(*) FROM play_match").query(Long.class).single())
                .isEqualTo(1);
        List<String> teams =
                jdbc.sql("""
                        SELECT team || ':' || attendance_id || ':' || (released_at IS NULL)
                        FROM match_player WHERE match_id = :id ORDER BY team""").param("id", matchId).query(String.class).list();
        assertThat(teams)
                .containsExactlyInAnyOrder(
                        "A:" + p.get(0) + ":true",
                        "A:" + p.get(1) + ":true",
                        "B:" + p.get(2) + ":true",
                        "B:" + p.get(3) + ":true");
        assertThat(teams.stream().map(t -> t.substring(0, 1)).toList()).containsExactly("A", "A", "B", "B");
        for (int i = 0; i < 8; i++) {
            assertThat(stateOf(p.get(i))).isEqualTo(i < 4 ? "ASSIGNED" : "READY");
        }
        assertThat(nextMatchNo(game)).isEqualTo(2);
        List<String> after = jdbc.sql(
                        "SELECT id || '|' || available_since || '|' || matches_played FROM attendance" + " ORDER BY id")
                .query(String.class)
                .list();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void aSecondConfirmOnAnotherCourtGetsMatchNumberTwo() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);

        HttpResponse<String> first = hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));
        HttpResponse<String> second = hostConfirm(game, 2, List.of(p.get(4), p.get(5)), List.of(p.get(6), p.get(7)));

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).as(second.body()).isEqualTo(201);
        assertThat(json(second).get("matchNo").asInt()).isEqualTo(2);
        assertThat(json(second).get("courtNo").asInt()).isEqualTo(2);
        assertThat(nextMatchNo(game)).isEqualTo(3);
    }

    // ---- TC-07 -----------------------------------------------------------------------------------------------

    @Test
    void aPlayerWhoIsPendingLeftOrRestingMakesTheConfirmA409AndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);

        for (String state : new String[] {"PENDING", "LEFT", "RESTING"}) {
            jdbc.sql("UPDATE attendance SET state = :s WHERE id = :id")
                    .param("s", state)
                    .param("id", p.get(3))
                    .update();
            Snapshot before = snapshot();

            HttpResponse<String> response =
                    hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

            assertMatchConflict(response);
            assertThat(snapshot()).as(state).isEqualTo(before);
        }
    }

    // ---- TC-08, TC-09, TC-10, TC-11 --------------------------------------------------------------------------

    @Test
    void aPlayerAlreadyInAPlannedMatchMakesTheConfirmA409() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        assertThat(hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)))
                        .statusCode())
                .isEqualTo(201);
        Snapshot before = snapshot();

        HttpResponse<String> response = hostConfirm(game, 2, List.of(p.get(0), p.get(4)), List.of(p.get(5), p.get(6)));

        assertMatchConflict(response);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aReadyPlayerWithAnUnreleasedMatchRowMakesTheConfirmA409() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        UUID other = insertMatch(game, 1, 2, "CANCELLED");
        matchPlayer(other, game, p.get(3), "A");
        Snapshot before = snapshot();

        HttpResponse<String> response = hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

        assertMatchConflict(response);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void aCourtWithAPlannedOrActiveMatchMakesTheConfirmA409() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        UUID busy = insertMatch(game, 1, 1, "PLANNED");

        for (String state : new String[] {"PLANNED", "ACTIVE"}) {
            jdbc.sql("UPDATE play_match SET state = :s WHERE id = :id")
                    .param("s", state)
                    .param("id", busy)
                    .update();
            Snapshot before = snapshot();

            HttpResponse<String> response =
                    hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

            assertMatchConflict(response);
            assertThat(snapshot()).as(state).isEqualTo(before);
        }
    }

    @Test
    void aCourtAboveTheCourtCountMakesTheConfirmA409() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        Snapshot before = snapshot();

        HttpResponse<String> response = hostConfirm(game, 3, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

        assertMatchConflict(response);
        assertThat(snapshot()).isEqualTo(before);
    }

    // ---- TC-12 -----------------------------------------------------------------------------------------------

    @Test
    void anAttendanceOfAnotherGameOrAnUnknownIdIsA409NotA404AndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        UUID otherGame = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 3);
        UUID foreign = attend(otherGame, "READY");
        Snapshot before = snapshot();

        HttpResponse<String> inOtherGame =
                hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), foreign));
        HttpResponse<String> unknown =
                hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), UUID.randomUUID()));

        assertMatchConflict(inOtherGame);
        assertMatchConflict(unknown);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(stateOf(foreign)).isEqualTo("READY");
    }

    // ---- TC-13 -----------------------------------------------------------------------------------------------

    @Test
    void aMalformedBodyIs400ValidationFailedAndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        Snapshot before = snapshot();
        String path = matchesPath(game);
        String token = token(host, "POST", path);
        String a = idList(List.of(p.get(0), p.get(1)));
        String b = idList(List.of(p.get(2), p.get(3)));

        for (String body : new String[] {
            body(1, List.of(p.get(0), p.get(0)), List.of(p.get(2), p.get(3))),
            body(1, List.of(p.get(0), p.get(1)), List.of(p.get(1), p.get(3))),
            body(1, List.of(p.get(0)), List.of(p.get(2), p.get(3))),
            body(1, List.of(p.get(0), p.get(1), p.get(2)), List.of(p.get(3))),
            "{\"courtNo\":1}",
            "{\"courtNo\":1,\"teams\":{\"A\":[\"abc\",\"" + p.get(1) + "\"],\"B\":" + b + "}}",
            "{\"courtNo\":0,\"teams\":{\"A\":" + a + ",\"B\":" + b + "}}",
            "{\"courtNo\":5,\"teams\":{\"A\":" + a + ",\"B\":" + b + "}}",
            ""
        }) {
            HttpResponse<String> response = post(path, token, body);

            assertThat(response.statusCode())
                    .as(body + " -> " + response.body())
                    .isEqualTo(400);
            assertThat(json(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(snapshot()).as(body).isEqualTo(before);
        }
    }

    // ---- TC-14, TC-15 ----------------------------------------------------------------------------------------

    private List<HttpResponse<String>> concurrently(UUID game, String first, String second) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<HttpResponse<String>> one = CompletableFuture.supplyAsync(() -> run(go, game, first));
        CompletableFuture<HttpResponse<String>> two = CompletableFuture.supplyAsync(() -> run(go, game, second));
        go.countDown();
        return List.of(one.get(), two.get());
    }

    private HttpResponse<String> run(CountDownLatch go, UUID game, String json) {
        try {
            go.await();
            return hostConfirm(game, json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertOneCreatedOneConflict(List<HttpResponse<String>> responses) {
        List<Integer> statuses =
                responses.stream().map(HttpResponse::statusCode).sorted().toList();
        assertThat(statuses).as(responses.toString()).containsExactly(201, 409);
        HttpResponse<String> conflict = responses.stream()
                .filter(r -> r.statusCode() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(json(conflict).get("code").asString()).isEqualTo("MATCH_CONFLICT");
    }

    @Test
    void twoConcurrentConfirmsSharingAPlayerOnDifferentCourtsSaveExactlyOneMatch() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);

        List<HttpResponse<String>> responses = concurrently(
                game,
                body(1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3))),
                body(2, List.of(p.get(3), p.get(4)), List.of(p.get(5), p.get(6))));

        assertOneCreatedOneConflict(responses);
        assertThat(jdbc.sql("SELECT count(*) FROM play_match").query(Long.class).single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM match_player")
                        .query(Long.class)
                        .single())
                .isEqualTo(4);
        assertThat(jdbc.sql("SELECT count(*) FROM attendance WHERE state = 'ASSIGNED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(4);
        assertThat(nextMatchNo(game)).isEqualTo(2);
    }

    @Test
    void twoConcurrentConfirmsOnTheSameCourtWithDifferentPlayersSaveExactlyOneMatch() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);

        List<HttpResponse<String>> responses = concurrently(
                game,
                body(1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3))),
                body(1, List.of(p.get(4), p.get(5)), List.of(p.get(6), p.get(7))));

        assertOneCreatedOneConflict(responses);
        assertThat(jdbc.sql("SELECT count(*) FROM play_match").query(Long.class).single())
                .isEqualTo(1);
    }

    // ---- TC-16 -----------------------------------------------------------------------------------------------

    @Test
    void otherAccountsMissingOrWrongTokensAndUnknownIdsAreRefusedAndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        String valid = body(1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));
        String path = matchesPath(game);
        String suggestionsPath = "/api/v1/host/games/" + game + "/suggestions";
        UUID unknown = UUID.randomUUID();
        Snapshot before = snapshot();

        HttpResponse<String> other = post(path, token(UUID.randomUUID(), "POST", path), valid);
        HttpResponse<String> none = post(path, null, valid);
        HttpResponse<String> wrongPath = post(path, token(host, "POST", suggestionsPath), valid);
        HttpResponse<String> unknownGame = post(matchesPath(unknown), token(host, "POST", matchesPath(unknown)), valid);
        HttpResponse<String> malformed =
                post(matchesPath("not-a-uuid"), token(host, "POST", matchesPath("not-a-uuid")), valid);

        assertCode(other, 403, "FORBIDDEN");
        assertCode(none, 401, "UNAUTHENTICATED");
        assertCode(wrongPath, 401, "UNAUTHENTICATED");
        assertCode(unknownGame, 404, "NOT_FOUND");
        assertCode(malformed, 404, "NOT_FOUND");
        assertThat(snapshot()).isEqualTo(before);
    }

    // Fix round 1: answers follow design 3.1 order 401, 404, 403, 400 even when the body is invalid.
    @Test
    void aBadBodyDoesNotHideAnUnauthenticatedUnknownOrForbiddenCaller() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        readyPlayers(game, 4);
        String path = matchesPath(game);
        UUID unknown = UUID.randomUUID();
        Snapshot before = snapshot();

        for (String bad : new String[] {"", "not json", "{}"}) {
            assertCode(post(path, null, bad), 401, "UNAUTHENTICATED");
            assertCode(post(path, token(UUID.randomUUID(), "POST", path), bad), 403, "FORBIDDEN");
            assertCode(post(matchesPath(unknown), token(host, "POST", matchesPath(unknown)), bad), 404, "NOT_FOUND");
            assertCode(post(path, token(host, "POST", path), bad), 400, "VALIDATION_FAILED");
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    // A guest caller kind does not exist in services yet (only the gateway knows the guest cookie), so the nearest
    // service-level row is a token issued for another audience: refused as no identity.
    @Test
    void aTokenIssuedForAnotherAudienceIs401AndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        String path = matchesPath(game);
        String foreign = new IdentityToken(SECRET, CLOCK).issue(host, "auth-service", "POST", path);
        Snapshot before = snapshot();

        HttpResponse<String> response =
                post(path, foreign, body(1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3))));

        assertCode(response, 401, "UNAUTHENTICATED");
        assertThat(snapshot()).isEqualTo(before);
    }

    // ---- TC-17 -----------------------------------------------------------------------------------------------

    @Test
    void aClosedGameIs409StateConflictAndNothingChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "CLOSED");
        List<UUID> p = readyPlayers(game, 4);
        Snapshot before = snapshot();

        HttpResponse<String> response = hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

        assertCode(response, 409, "STATE_CONFLICT");
        assertThat(snapshot()).isEqualTo(before);
    }

    // ---- TC-18 -----------------------------------------------------------------------------------------------

    @Test
    void afterAConfirmTheBoardShowsTheMatchAndTheNextSuggestionLeavesItOut() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        assertThat(hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)))
                        .statusCode())
                .isEqualTo(201);
        String boardPath = "/api/v1/host/games/" + game + "/board";
        String suggestionsPath = "/api/v1/host/games/" + game + "/suggestions";

        HttpResponse<String> board = get(boardPath, token(host, "GET", boardPath));
        HttpResponse<String> suggestions = post(suggestionsPath, token(host, "POST", suggestionsPath), "{}");

        assertThat(board.statusCode()).as(board.body()).isEqualTo(200);
        JsonNode court1 = json(board).get("courts").get(0);
        assertThat(court1.get("courtNo").asInt()).isEqualTo(1);
        assertThat(court1.get("match").get("state").asString()).isEqualTo("PLANNED");
        assertThat(court1.get("match").get("matchNo").asInt()).isEqualTo(1);
        assertThat(ids(court1.get("match").get("teams").get("A")))
                .containsExactlyInAnyOrder(p.get(0).toString(), p.get(1).toString());
        assertThat(ids(court1.get("match").get("teams").get("B")))
                .containsExactlyInAnyOrder(p.get(2).toString(), p.get(3).toString());
        assertThat(suggestions.statusCode()).as(suggestions.body()).isEqualTo(200);
        JsonNode proposals = json(suggestions).get("suggestions");
        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).get("courtNo").asInt()).isEqualTo(2);
        Set<String> suggested = new HashSet<>();
        suggested.addAll(ids(proposals.get(0).get("teams").get("A")));
        suggested.addAll(ids(proposals.get(0).get("teams").get("B")));
        for (int i = 0; i < 4; i++) {
            assertThat(suggested).doesNotContain(p.get(i).toString());
        }
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asString());
        }
        return out;
    }

    // ---- TC-19 -----------------------------------------------------------------------------------------------

    @Test
    void aSinglesGameTakesOnePlusOneAndRefusesTwoPlusTwo() throws Exception {
        UUID singles = insertGame(1, "SINGLES", "OPEN");
        List<UUID> s = readyPlayers(singles, 2);

        HttpResponse<String> ok = hostConfirm(singles, 1, List.of(s.get(0)), List.of(s.get(1)));

        assertThat(ok.statusCode()).as(ok.body()).isEqualTo(201);
        assertThat(jdbc.sql("SELECT count(*) FROM match_player WHERE game_id = :g")
                        .param("g", singles)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(stateOf(s.get(0))).isEqualTo("ASSIGNED");
        assertThat(stateOf(s.get(1))).isEqualTo("ASSIGNED");

        UUID fresh = insertGame(1, "SINGLES", "OPEN");
        List<UUID> f = readyPlayers(fresh, 4);
        Snapshot before = snapshot();

        HttpResponse<String> bad = hostConfirm(fresh, 1, List.of(f.get(0), f.get(1)), List.of(f.get(2), f.get(3)));

        assertCode(bad, 400, "VALIDATION_FAILED");
        assertThat(snapshot()).isEqualTo(before);
    }
}
