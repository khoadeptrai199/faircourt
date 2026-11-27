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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
 * {@code PATCH /api/v1/host/matches/{matchId}/start|finish|cancel} against PostgreSQL with a test identity token (Match
 * lifecycle plan, TC-03 to TC-17). Fixtures are made through the real confirm route. player-service is a JDK HTTP stub,
 * used only where suggestions are read (TC-12, TC-13).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchLifecycleIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final String SERVICE = "playmatch-service";
    private static final Instant T = Instant.parse("2026-11-27T12:30:00Z");
    private static final Instant BASE = Instant.parse("2026-11-27T10:00:00Z");
    private static final Instant WAITING_SINCE = BASE.plusSeconds(300);
    private static final String CONFLICT_MESSAGE = "This changed in the meantime. Refresh and try again.";

    /** The application's clock, fixed at {@code T}; tokens and written times follow it. */
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
    private static final HttpServer PLAYER = startPlayerStub();

    private static HttpServer startPlayerStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", MatchLifecycleIT::answerPlayer);
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

    private static String actionPath(Object matchId, String action) {
        return "/api/v1/host/matches/" + matchId + "/" + action;
    }

    private static String token(UUID user, String method, String path) {
        return new IdentityToken(SECRET, CLOCK).issue(user, SERVICE, method, path);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> hostAction(UUID matchId, String action) throws Exception {
        return hostAction(host, matchId, action);
    }

    private HttpResponse<String> hostAction(UUID caller, Object matchId, String action) throws Exception {
        String path = actionPath(matchId, action);
        return send("PATCH", path, token(caller, "PATCH", path), "");
    }

    private HttpResponse<String> hostConfirm(UUID game, int court, List<UUID> a, List<UUID> b) throws Exception {
        String path = matchesPath(game);
        return send("POST", path, token(host, "POST", path), body(court, a, b));
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

    /** Confirms a DOUBLES match through the real route and returns its id. */
    private UUID confirmed(UUID game, int court, List<UUID> four) throws Exception {
        HttpResponse<String> response =
                hostConfirm(game, court, List.of(four.get(0), four.get(1)), List.of(four.get(2), four.get(3)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(json(response).get("matchId").asString());
    }

    private void ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    /** A match brought to {@code state} through the real routes, with four fresh players on court 1 of a new game. */
    private record Fixture(UUID game, UUID match, List<UUID> players) {}

    private Fixture matchIn(String state) throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        UUID match = confirmed(game, 1, p);
        switch (state) {
            case "PLANNED" -> {}
            case "ACTIVE" -> ok(hostAction(match, "start"));
            case "FINISHED" -> {
                ok(hostAction(match, "start"));
                ok(hostAction(match, "finish"));
            }
            case "CANCELLED" -> ok(hostAction(match, "cancel"));
            default -> throw new IllegalArgumentException(state);
        }
        return new Fixture(game, match, p);
    }

    private UUID insertGame(int courts, String format, String state) {
        return insertGame(courts, format, state, host);
    }

    private UUID insertGame(int courts, String format, String state, UUID hostUser) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
						INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state,
						                  match_format, min_sets)
						VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, :courts, :state,
						        :format, 6)""")
                .param("id", id)
                .param("code", id.toString().substring(0, 8).toUpperCase())
                .param("host", hostUser)
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
        jdbc.sql("""
						INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
						                        matches_played)
						VALUES (:id, :game, :player, :name, :state, :checkedIn, :available, 0)""")
                .param("id", id)
                .param("game", game)
                .param("player", UUID.randomUUID())
                .param("name", "Zorbulon " + id.toString().substring(0, 4))
                .param("state", state)
                .param("checkedIn", OffsetDateTime.ofInstant(BASE.minusSeconds(60), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(WAITING_SINCE, ZoneOffset.UTC))
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

    /** Everything a refused command must leave alone. */
    private record Snapshot(
            List<String> matches, List<String> matchPlayers, List<String> attendance, List<Integer> next) {}

    private Snapshot snapshot() {
        return new Snapshot(
                jdbc.sql("SELECT id || '|' || state || '|' || coalesce(started_at::text, '-') || '|'"
                                + " || coalesce(finished_at::text, '-') FROM play_match ORDER BY id")
                        .query(String.class)
                        .list(),
                jdbc.sql("SELECT match_id || '|' || attendance_id || '|' || coalesce(released_at::text, '-')"
                                + " FROM match_player ORDER BY match_id, attendance_id")
                        .query(String.class)
                        .list(),
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

    private int setsOf(UUID attendance) {
        return jdbc.sql("SELECT matches_played FROM attendance WHERE id = :id")
                .param("id", attendance)
                .query(Integer.class)
                .single();
    }

    private Instant availableSince(UUID attendance) {
        return jdbc.sql("SELECT available_since FROM attendance WHERE id = :id")
                .param("id", attendance)
                .query((rs, n) -> rs.getTimestamp(1).toInstant())
                .single();
    }

    private String matchState(UUID match) {
        return jdbc.sql("SELECT state FROM play_match WHERE id = :id")
                .param("id", match)
                .query(String.class)
                .single();
    }

    private Instant matchTime(UUID match, String column) {
        // list().getFirst(): a nullable column maps to null, which single() refuses.
        return jdbc.sql("SELECT " + column + " FROM play_match WHERE id = :id")
                .param("id", match)
                .query((rs, n) ->
                        rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant())
                .list()
                .getFirst();
    }

    private List<Instant> releasedAt(UUID match) {
        return jdbc.sql("SELECT released_at FROM match_player WHERE match_id = :id")
                .param("id", match)
                .query((rs, n) ->
                        rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant())
                .list();
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static void assertCode(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response).get("code").asString()).isEqualTo(code);
    }

    private static void assertStateConflict(HttpResponse<String> response) {
        assertCode(response, 409, "STATE_CONFLICT");
        assertThat(json(response).get("message").asString()).isEqualTo(CONFLICT_MESSAGE);
    }

    private static String exactBody(UUID match, int matchNo, int courtNo, String state) {
        return "{\"matchId\":\"" + match + "\",\"matchNo\":" + matchNo + ",\"courtNo\":" + courtNo + ",\"state\":\""
                + state + "\"}";
    }

    // ---- TC-03 -----------------------------------------------------------------------------------------------

    @Test
    void startingAPlannedMatchMakesItActiveAndItsPlayersPlayingAndNothingElseChanges() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        UUID match = confirmed(game, 1, p.subList(0, 4));

        HttpResponse<String> response = hostAction(match, "start");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonAllowlist.assertOnlyFields(response.body(), "matchId", "matchNo", "courtNo", "state");
        assertThat(response.body()).isEqualTo(exactBody(match, 1, 1, "ACTIVE"));
        assertThat(matchTime(match, "started_at")).isEqualTo(T);
        assertThat(matchTime(match, "finished_at")).isNull();
        assertThat(releasedAt(match)).hasSize(4).containsOnlyNulls();
        for (int i = 0; i < 8; i++) {
            assertThat(stateOf(p.get(i))).isEqualTo(i < 4 ? "PLAYING" : "READY");
            assertThat(setsOf(p.get(i))).isZero();
            assertThat(availableSince(p.get(i))).isEqualTo(WAITING_SINCE);
        }
        assertThat(jdbc.sql("SELECT next_match_no FROM game WHERE id = :id")
                        .param("id", game)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }

    // ---- TC-04 -----------------------------------------------------------------------------------------------

    @Test
    void finishingAnActiveMatchReleasesItsPlayersCountsOneSetAndResetsTheirWaitTime() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        UUID match = confirmed(game, 1, p.subList(0, 4));
        ok(hostAction(match, "start"));
        List<String> waiting = jdbc.sql("SELECT id || '|' || state || '|' || available_since || '|' || matches_played"
                        + " FROM attendance WHERE id IN (:ids) ORDER BY id")
                .param("ids", p.subList(4, 8))
                .query(String.class)
                .list();

        HttpResponse<String> response = hostAction(match, "finish");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), "matchId", "matchNo", "courtNo", "state");
        assertThat(response.body()).isEqualTo(exactBody(match, 1, 1, "FINISHED"));
        assertThat(matchTime(match, "finished_at")).isEqualTo(T);
        assertThat(releasedAt(match)).hasSize(4).containsOnly(T);
        for (int i = 0; i < 4; i++) {
            assertThat(stateOf(p.get(i))).isEqualTo("READY");
            assertThat(setsOf(p.get(i))).isEqualTo(1);
            assertThat(availableSince(p.get(i))).isEqualTo(T);
        }
        List<String> waitingAfter = jdbc.sql(
                        "SELECT id || '|' || state || '|' || available_since || '|' || matches_played"
                                + " FROM attendance WHERE id IN (:ids) ORDER BY id")
                .param("ids", p.subList(4, 8))
                .query(String.class)
                .list();
        assertThat(waitingAfter).isEqualTo(waiting);
    }

    // ---- TC-05, TC-06 ----------------------------------------------------------------------------------------

    @Test
    void cancellingAPlannedMatchReleasesItsPlayersWithoutCountingASet() throws Exception {
        Fixture f = matchIn("PLANNED");

        HttpResponse<String> response = hostAction(f.match(), "cancel");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(exactBody(f.match(), 1, 1, "CANCELLED"));
        assertThat(releasedAt(f.match())).hasSize(4).containsOnly(T);
        for (UUID id : f.players()) {
            assertThat(stateOf(id)).isEqualTo("READY");
            assertThat(setsOf(id)).isZero();
            assertThat(availableSince(id)).isEqualTo(WAITING_SINCE);
        }
        assertThat(matchTime(f.match(), "started_at")).isNull();
        assertThat(matchTime(f.match(), "finished_at")).isNull();
    }

    @Test
    void cancellingAnActiveMatchReleasesItsPlayersKeepsStartedAtAndLeavesFinishedAtEmpty() throws Exception {
        Fixture f = matchIn("ACTIVE");

        HttpResponse<String> response = hostAction(f.match(), "cancel");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(exactBody(f.match(), 1, 1, "CANCELLED"));
        assertThat(releasedAt(f.match())).hasSize(4).containsOnly(T);
        for (UUID id : f.players()) {
            assertThat(stateOf(id)).isEqualTo("READY");
            assertThat(setsOf(id)).isZero();
            assertThat(availableSince(id)).isEqualTo(WAITING_SINCE);
        }
        assertThat(matchTime(f.match(), "started_at")).isEqualTo(T);
        assertThat(matchTime(f.match(), "finished_at")).isNull();
    }

    // ---- TC-07 -----------------------------------------------------------------------------------------------

    @Test
    void everyIllegalMoveIs409StateConflictAndNothingChanges() throws Exception {
        String[][] illegal = {
            {"ACTIVE", "start"},
            {"FINISHED", "start"},
            {"CANCELLED", "start"},
            {"PLANNED", "finish"},
            {"FINISHED", "finish"},
            {"CANCELLED", "finish"},
            {"FINISHED", "cancel"},
            {"CANCELLED", "cancel"}
        };
        for (String[] move : illegal) {
            Fixture f = matchIn(move[0]);
            Snapshot before = snapshot();

            HttpResponse<String> response = hostAction(f.match(), move[1]);

            assertStateConflict(response);
            assertThat(snapshot()).as(move[0] + " " + move[1]).isEqualTo(before);
        }
    }

    // ---- TC-08 -----------------------------------------------------------------------------------------------

    @Test
    void repeatingTheSameActionIsAConflictTheSecondTimeAndCountsOneSetOnly() throws Exception {
        Fixture started = matchIn("PLANNED");
        ok(hostAction(started.match(), "start"));
        Snapshot afterStart = snapshot();
        assertStateConflict(hostAction(started.match(), "start"));
        assertThat(snapshot()).isEqualTo(afterStart);

        ok(hostAction(started.match(), "finish"));
        Snapshot afterFinish = snapshot();
        assertStateConflict(hostAction(started.match(), "finish"));
        assertThat(snapshot()).isEqualTo(afterFinish);
        for (UUID id : started.players()) {
            assertThat(setsOf(id)).isEqualTo(1);
        }

        Fixture cancelled = matchIn("PLANNED");
        ok(hostAction(cancelled.match(), "cancel"));
        Snapshot afterCancel = snapshot();
        assertStateConflict(hostAction(cancelled.match(), "cancel"));
        assertThat(snapshot()).isEqualTo(afterCancel);
    }

    // ---- TC-09 -----------------------------------------------------------------------------------------------

    @Test
    void aClosedGameIs409ForAllThreeActionsAndNothingChanges() throws Exception {
        Fixture f = matchIn("ACTIVE");
        jdbc.sql("UPDATE game SET state = 'CLOSED' WHERE id = :id")
                .param("id", f.game())
                .update();
        Snapshot before = snapshot();

        for (String action : new String[] {"start", "finish", "cancel"}) {
            assertCode(hostAction(f.match(), action), 409, "STATE_CONFLICT");
            assertThat(snapshot()).as(action).isEqualTo(before);
        }
    }

    // ---- TC-10 -----------------------------------------------------------------------------------------------

    @Test
    void aPlayerOutOfStepWithTheMatchMakesTheActionA409AndNothingChanges() throws Exception {
        Fixture active = matchIn("ACTIVE");
        jdbc.sql("UPDATE attendance SET state = 'READY' WHERE id = :id")
                .param("id", active.players().get(0))
                .update();
        Snapshot beforeFinish = snapshot();

        assertStateConflict(hostAction(active.match(), "finish"));

        assertThat(snapshot()).isEqualTo(beforeFinish);
        assertThat(matchState(active.match())).isEqualTo("ACTIVE");

        Fixture planned = matchIn("PLANNED");
        jdbc.sql("UPDATE attendance SET state = 'PLAYING' WHERE id = :id")
                .param("id", planned.players().get(0))
                .update();
        Snapshot beforeStart = snapshot();

        assertStateConflict(hostAction(planned.match(), "start"));

        assertThat(snapshot()).isEqualTo(beforeStart);
    }

    // ---- TC-11 -----------------------------------------------------------------------------------------------

    @Test
    void afterAFinishOrACancelTheSameCourtAndPlayersCanBeConfirmedAgain() throws Exception {
        for (String end : new String[] {"finish", "cancel"}) {
            UUID game = insertGame(2, "DOUBLES", "OPEN");
            List<UUID> p = readyPlayers(game, 4);
            UUID first = confirmed(game, 1, p);
            if (end.equals("finish")) {
                ok(hostAction(first, "start"));
            }
            ok(hostAction(first, end));

            HttpResponse<String> again = hostConfirm(game, 1, List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3)));

            assertThat(again.statusCode()).as(end + " " + again.body()).isEqualTo(201);
            assertThat(json(again).get("state").asString()).isEqualTo("PLANNED");
            assertThat(json(again).get("matchNo").asInt()).isEqualTo(2);
            assertThat(jdbc.sql("SELECT count(*) FROM play_match WHERE game_id = :g AND court_no = 1"
                                    + " AND state IN ('PLANNED','ACTIVE')")
                            .param("g", game)
                            .query(Long.class)
                            .single())
                    .isEqualTo(1);
        }
    }

    // ---- TC-12, TC-13 ----------------------------------------------------------------------------------------

    private JsonNode suggestions(UUID game) throws Exception {
        String path = "/api/v1/host/games/" + game + "/suggestions";
        HttpResponse<String> response = send("POST", path, token(host, "POST", path), "{}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).get("suggestions");
    }

    private static Set<String> playersOf(JsonNode proposal) {
        Set<String> ids = new HashSet<>();
        for (String team : new String[] {"A", "B"}) {
            for (JsonNode id : proposal.get("teams").get(team)) {
                ids.add(id.asString());
            }
        }
        return ids;
    }

    @Test
    void afterAFinishTheNextSuggestionPicksThePlayersWhoSatOut() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 8);
        UUID match = confirmed(game, 1, p.subList(0, 4));
        ok(hostAction(match, "start"));
        ok(hostAction(match, "finish"));

        JsonNode proposals = suggestions(game);

        assertThat(proposals).hasSize(1);
        assertThat(proposals.get(0).get("courtNo").asInt()).isEqualTo(1);
        Set<String> suggested = playersOf(proposals.get(0));
        assertThat(suggested).hasSize(4);
        for (int i = 0; i < 4; i++) {
            assertThat(suggested).doesNotContain(p.get(i).toString());
        }
        for (int i = 4; i < 8; i++) {
            assertThat(suggested).contains(p.get(i).toString());
        }
    }

    @Test
    void afterACancelTheSamePlayersCanBeSuggestedAgain() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        List<UUID> p = readyPlayers(game, 4);
        UUID match = confirmed(game, 1, p);
        ok(hostAction(match, "cancel"));

        JsonNode proposals = suggestions(game);

        assertThat(proposals).hasSize(1);
        Set<String> suggested = playersOf(proposals.get(0));
        for (UUID id : p) {
            assertThat(suggested).contains(id.toString());
        }
    }

    // ---- TC-14 -----------------------------------------------------------------------------------------------

    @Test
    void aSinglesMatchMovesBothPlayersAndCountsOneSetForEach() throws Exception {
        UUID game = insertGame(1, "SINGLES", "OPEN");
        List<UUID> s = readyPlayers(game, 2);
        HttpResponse<String> confirm = hostConfirm(game, 1, List.of(s.get(0)), List.of(s.get(1)));
        assertThat(confirm.statusCode()).as(confirm.body()).isEqualTo(201);
        UUID match = UUID.fromString(json(confirm).get("matchId").asString());

        ok(hostAction(match, "start"));

        assertThat(stateOf(s.get(0))).isEqualTo("PLAYING");
        assertThat(stateOf(s.get(1))).isEqualTo("PLAYING");

        ok(hostAction(match, "finish"));

        assertThat(stateOf(s.get(0))).isEqualTo("READY");
        assertThat(stateOf(s.get(1))).isEqualTo("READY");
        assertThat(releasedAt(match)).hasSize(2).containsOnly(T);
        assertThat(setsOf(s.get(0))).isEqualTo(1);
        assertThat(setsOf(s.get(1))).isEqualTo(1);
    }

    // ---- TC-15, TC-16 ----------------------------------------------------------------------------------------

    private List<HttpResponse<String>> concurrently(UUID match, String first, String second) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<HttpResponse<String>> one = CompletableFuture.supplyAsync(() -> run(go, match, first));
        CompletableFuture<HttpResponse<String>> two = CompletableFuture.supplyAsync(() -> run(go, match, second));
        go.countDown();
        return List.of(one.get(), two.get());
    }

    private HttpResponse<String> run(CountDownLatch go, UUID match, String action) {
        try {
            go.await();
            return hostAction(match, action);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void twoConcurrentFinishesOfTheSameMatchCountEachPlayersSetOnce() throws Exception {
        Fixture f = matchIn("ACTIVE");

        List<HttpResponse<String>> responses = concurrently(f.match(), "finish", "finish");

        assertThat(responses.stream().map(HttpResponse::statusCode).sorted().toList())
                .as(responses.toString())
                .containsExactly(200, 409);
        HttpResponse<String> conflict = responses.stream()
                .filter(r -> r.statusCode() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(json(conflict).get("code").asString()).isEqualTo("STATE_CONFLICT");
        assertThat(matchState(f.match())).isEqualTo("FINISHED");
        assertThat(matchTime(f.match(), "finished_at")).isEqualTo(T);
        for (UUID id : f.players()) {
            assertThat(setsOf(id)).isEqualTo(1);
            assertThat(stateOf(id)).isEqualTo("READY");
        }
    }

    @Test
    void aConcurrentFinishAndCancelOfTheSameMatchLeaveOneWinnerAndConsistentPlayers() throws Exception {
        Fixture f = matchIn("ACTIVE");

        List<HttpResponse<String>> responses = concurrently(f.match(), "finish", "cancel");

        assertThat(responses.stream().map(HttpResponse::statusCode).sorted().toList())
                .as(responses.toString())
                .containsExactly(200, 409);
        String winner = responses.stream()
                .filter(r -> r.statusCode() == 200)
                .map(r -> json(r).get("state").asString())
                .findFirst()
                .orElseThrow();
        assertThat(matchState(f.match())).isEqualTo(winner);
        assertThat(releasedAt(f.match())).hasSize(4).containsOnly(T);
        for (UUID id : f.players()) {
            assertThat(stateOf(id)).isEqualTo("READY");
            assertThat(setsOf(id)).isEqualTo(winner.equals("FINISHED") ? 1 : 0);
        }
    }

    // ---- TC-17 -----------------------------------------------------------------------------------------------

    @Test
    void otherAccountsMissingOrWrongTokensAndUnknownIdsAreRefusedAndNothingChanges() throws Exception {
        Fixture f = matchIn("PLANNED");
        UUID otherHost = UUID.randomUUID();
        insertGame(1, "DOUBLES", "OPEN", otherHost);
        Snapshot before = snapshot();
        UUID unknown = UUID.randomUUID();

        for (String action : new String[] {"start", "finish", "cancel"}) {
            String path = actionPath(f.match(), action);
            String wrongPath = actionPath(f.match(), action.equals("start") ? "finish" : "start");

            HttpResponse<String> other = hostAction(UUID.randomUUID(), f.match(), action);
            HttpResponse<String> none = send("PATCH", path, null, "");
            HttpResponse<String> boundElsewhere = send("PATCH", path, token(host, "PATCH", wrongPath), "");
            HttpResponse<String> wrongMethod = send("PATCH", path, token(host, "POST", path), "");
            HttpResponse<String> unknownMatch = hostAction(host, unknown, action);
            HttpResponse<String> malformed = hostAction(host, "abc", action);
            // The host of another game owns a real match, but not this one.
            HttpResponse<String> otherGamesHost = hostAction(otherHost, f.match(), action);

            assertCode(other, 403, "FORBIDDEN");
            assertCode(none, 401, "UNAUTHENTICATED");
            assertCode(boundElsewhere, 401, "UNAUTHENTICATED");
            assertCode(wrongMethod, 401, "UNAUTHENTICATED");
            assertCode(unknownMatch, 404, "NOT_FOUND");
            assertCode(malformed, 404, "NOT_FOUND");
            assertCode(otherGamesHost, 403, "FORBIDDEN");
            assertThat(snapshot()).as(action).isEqualTo(before);
        }
    }
}
