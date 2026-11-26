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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}} against PostgreSQL with a test identity token
 * (Attendance management plan, TC-02 to TC-10, TC-13 to TC-16). player-service is a JDK HTTP stub, used only by the
 * member re-join check of TC-16.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class HostAttendanceIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final String SERVICE = "playmatch-service";
    private static final Instant T0 = Instant.parse("2026-11-26T10:00:00Z");
    private static final UUID MEMBER_PLAYER = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String PLAYER_STUB_ANSWER = "{\"playerId\":\"" + MEMBER_PLAYER + "\",\"displayName\":\"Mem\"}";

    /** Stand-in for player-service: answers the member find-or-create with one fixed profile. */
    private static final HttpServer PLAYER = startPlayerStub();

    private static HttpServer startPlayerStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", HostAttendanceIT::answerPlayer);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answerPlayer(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = PLAYER_STUB_ANSWER.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
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

    private static String path(UUID game, UUID attendance) {
        return "/api/v1/host/games/" + game + "/attendance/" + attendance;
    }

    private static String token(UUID user, String method, String path) {
        return new IdentityToken(SECRET, Clock.systemUTC()).issue(user, SERVICE, method, path);
    }

    private HttpResponse<String> patch(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The game's host sends {@code {"state": state}}. */
    private HttpResponse<String> hostSets(UUID game, UUID attendance, String state) throws Exception {
        String path = path(game, attendance);
        return patch(path, token(host, "PATCH", path), "{\"state\":\"" + state + "\"}");
    }

    private UUID insertGame(String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state)
				VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, 2, :state)""")
                .param("id", id)
                .param("code", id.toString().substring(0, 8).toUpperCase())
                .param("host", host)
                .param("startsAt", OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(T0.plusSeconds(7200), ZoneOffset.UTC))
                .param("state", state)
                .update();
        return id;
    }

    private UUID attend(UUID game, UUID player, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
				                        matches_played)
				VALUES (:id, :game, :player, 'Some Player', :state, :checkedIn, :available, 3)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("state", state)
                .param("checkedIn", OffsetDateTime.ofInstant(T0.minusSeconds(600), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(T0, ZoneOffset.UTC))
                .update();
        return id;
    }

    private UUID attend(UUID game, String state) {
        return attend(game, UUID.randomUUID(), state);
    }

    /** One match of the game in the given state with the attendance as its only (unreleased) player. */
    private void putInMatch(UUID game, UUID attendance, int matchNo, int court, String matchState) {
        UUID match = UUID.randomUUID();
        jdbc.sql("INSERT INTO play_match (id, game_id, match_no, court_no, state) VALUES (:id, :game, :no, :court, :s)")
                .param("id", match)
                .param("game", game)
                .param("no", matchNo)
                .param("court", court)
                .param("s", matchState)
                .update();
        jdbc.sql("INSERT INTO match_player (match_id, game_id, attendance_id, team) VALUES (:m, :g, :a, 'A')")
                .param("m", match)
                .param("g", game)
                .param("a", attendance)
                .update();
    }

    private record Snapshot(String state, Instant availableSince, int matchesPlayed) {}

    private Snapshot row(UUID attendance) {
        return jdbc.sql("SELECT state, available_since, matches_played FROM attendance WHERE id = :id")
                .param("id", attendance)
                .query((rs, n) -> new Snapshot(
                        rs.getString("state"),
                        rs.getTimestamp("available_since").toInstant(),
                        rs.getInt("matches_played")))
                .single();
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    private static void assertConflict(HttpResponse<String> response, String message) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("STATE_CONFLICT");
        assertThat(body.get("message").asString()).isEqualTo(message);
    }

    // ---- TC-02, TC-15 ----------------------------------------------------------------------------------------

    @Test
    void theHostApprovesAPendingGuestWithoutChangingAvailableSinceAndTheAnswerHasExactlyThreeFields() throws Exception {
        UUID game = insertGame("OPEN");
        UUID guest = attend(game, "PENDING");

        HttpResponse<String> response = hostSets(game, guest, "READY");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("attendanceId").asString()).isEqualTo(guest.toString());
        assertThat(body.get("state").asString()).isEqualTo("READY");
        assertThat(Instant.parse(body.get("availableSince").asString())).isEqualTo(T0);
        JsonAllowlist.assertOnlyFields(response.body(), "attendanceId", "state", "availableSince");
        assertThat(row(guest)).isEqualTo(new Snapshot("READY", T0, 3));
    }

    // ---- TC-03, TC-04, TC-05 ---------------------------------------------------------------------------------

    @Test
    void theHostCanRejectAPendingGuestAndMarkAReadyPlayerAsLeft() throws Exception {
        UUID game = insertGame("OPEN");
        UUID pending = attend(game, "PENDING");
        UUID ready = attend(game, "READY");

        HttpResponse<String> reject = hostSets(game, pending, "LEFT");
        HttpResponse<String> left = hostSets(game, ready, "LEFT");

        assertThat(reject.statusCode()).as(reject.body()).isEqualTo(200);
        assertThat(JSON.readTree(reject.body()).get("state").asString()).isEqualTo("LEFT");
        assertThat(left.statusCode()).as(left.body()).isEqualTo(200);
        assertThat(JSON.readTree(left.body()).get("state").asString()).isEqualTo("LEFT");
        assertThat(row(pending)).isEqualTo(new Snapshot("LEFT", T0, 3));
        assertThat(row(ready)).isEqualTo(new Snapshot("LEFT", T0, 3));
    }

    @Test
    void settingTheSameStateAnswers200WithTheUnchangedRow() throws Exception {
        UUID game = insertGame("OPEN");
        UUID ready = attend(game, "READY");

        HttpResponse<String> response = hostSets(game, ready, "READY");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("state").asString()).isEqualTo("READY");
        assertThat(Instant.parse(body.get("availableSince").asString())).isEqualTo(T0);
        assertThat(row(ready)).isEqualTo(new Snapshot("READY", T0, 3));
    }

    // ---- TC-06 -----------------------------------------------------------------------------------------------

    @Test
    void aPlayerInAPlannedOrActiveMatchCannotBeChangedAndNothingIsWritten() throws Exception {
        UUID game = insertGame("OPEN");
        UUID assigned = attend(game, "ASSIGNED");
        UUID playing = attend(game, "PLAYING");
        putInMatch(game, assigned, 1, 1, "PLANNED");
        putInMatch(game, playing, 2, 2, "ACTIVE");
        int attendanceRows = count("attendance");
        int matches = count("play_match");
        int matchPlayers = count("match_player");

        for (UUID id : List.of(assigned, playing)) {
            for (String target : new String[] {"LEFT", "READY"}) {
                assertConflict(hostSets(game, id, target), "Cancel or finish this player's match first.");
            }
        }

        assertThat(row(assigned).state()).isEqualTo("ASSIGNED");
        assertThat(row(playing).state()).isEqualTo("PLAYING");
        assertThat(count("attendance")).isEqualTo(attendanceRows);
        assertThat(count("play_match")).isEqualTo(matches);
        assertThat(count("match_player")).isEqualTo(matchPlayers);
    }

    // ---- TC-07 -----------------------------------------------------------------------------------------------

    @Test
    void leftIsFinalAndRestingCannotBeChangedByTheHost() throws Exception {
        UUID game = insertGame("OPEN");
        UUID left = attend(game, "LEFT");
        UUID resting = attend(game, "RESTING");

        for (HttpResponse<String> response : List.of(
                hostSets(game, left, "READY"),
                hostSets(game, left, "READY"),
                hostSets(game, resting, "READY"),
                hostSets(game, resting, "LEFT"))) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("STATE_CONFLICT");
        }
        HttpResponse<String> sameLeft = hostSets(game, left, "LEFT");

        assertThat(row(left).state()).isEqualTo("LEFT");
        assertThat(row(resting).state()).isEqualTo("RESTING");
        assertThat(sameLeft.statusCode()).as(sameLeft.body()).isEqualTo(200);
        assertThat(JSON.readTree(sameLeft.body()).get("state").asString()).isEqualTo("LEFT");
    }

    // ---- TC-08 -----------------------------------------------------------------------------------------------

    @Test
    void aClosedGameAnswers409AndNothingChanges() throws Exception {
        UUID game = insertGame("CLOSED");
        UUID pending = attend(game, "PENDING");

        assertConflict(hostSets(game, pending, "READY"), "This game is closed.");

        assertThat(row(pending)).isEqualTo(new Snapshot("PENDING", T0, 3));
    }

    // ---- TC-09 -----------------------------------------------------------------------------------------------

    @Test
    void anInvalidBodyAnswers400AndAnUnknownFieldIsIgnored() throws Exception {
        UUID game = insertGame("OPEN");
        UUID pending = attend(game, "PENDING");
        String path = path(game, pending);
        String token = token(host, "PATCH", path);

        for (String body : new String[] {
            "{}",
            "{\"state\":null}",
            "{\"state\":\"RESTING\"}",
            "{\"state\":\"PLAYING\"}",
            "{\"state\":\"PENDING\"}",
            "{\"state\":\"FOO\"}",
            "not json"
        }) {
            HttpResponse<String> response = patch(path, token, body);

            assertThat(response.statusCode())
                    .as(body + " -> " + response.body())
                    .isEqualTo(400);
            assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(row(pending).state()).as(body).isEqualTo("PENDING");
        }

        HttpResponse<String> extra = patch(path, token, "{\"state\":\"READY\",\"extra\":1}");

        assertThat(extra.statusCode()).as(extra.body()).isEqualTo(200);
        assertThat(row(pending).state()).isEqualTo("READY");
    }

    // ---- TC-10 -----------------------------------------------------------------------------------------------

    @Test
    void anAttendanceOfAnotherGameOfTheSameHostIsNotFound() throws Exception {
        UUID gameA = insertGame("OPEN");
        UUID gameB = insertGame("OPEN");
        UUID inB = attend(gameB, "PENDING");

        HttpResponse<String> response = hostSets(gameA, inB, "READY");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(row(inB)).isEqualTo(new Snapshot("PENDING", T0, 3));
    }

    // ---- TC-13 -----------------------------------------------------------------------------------------------

    @Test
    void aTokenIssuedForTheBoardReadCannotBeReplayedOnThePatch() throws Exception {
        UUID game = insertGame("OPEN");
        UUID pending = attend(game, "PENDING");
        String boardToken = token(host, "GET", "/api/v1/host/games/" + game + "/board");

        HttpResponse<String> response = patch(path(game, pending), boardToken, "{\"state\":\"READY\"}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(row(pending).state()).isEqualTo("PENDING");
    }
    // Observed order (Fix round 1): every argument is resolved before the method body runs, so a bad body is 400
    // even without a token; the account check (401) comes with a valid body.
    @Test
    void aMissingTokenWithAnInvalidBodyAnswers400AndWithAValidBodyAnswers401() throws Exception {
        UUID game = insertGame("OPEN");
        UUID pending = attend(game, "PENDING");
        String path = path(game, pending);

        HttpResponse<String> badBody = patch(path, null, "{}");
        HttpResponse<String> goodBody = patch(path, null, "{\"state\":\"READY\"}");

        assertThat(badBody.statusCode()).as(badBody.body()).isEqualTo(400);
        assertThat(goodBody.statusCode()).as(goodBody.body()).isEqualTo(401);
        assertThat(row(pending).state()).isEqualTo("PENDING");
    }

    // ---- TC-14 -----------------------------------------------------------------------------------------------

    @Test
    void twoParallelApprovalsBothAnswer200AndTheRowIsReadyOnce() throws Exception {
        UUID game = insertGame("OPEN");
        UUID pending = attend(game, "PENDING");

        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() -> {
            try {
                return hostSets(game, pending, "READY");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() -> {
            try {
                return hostSets(game, pending, "READY");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(first.get().statusCode()).as(first.get().body()).isEqualTo(200);
        assertThat(second.get().statusCode()).as(second.get().body()).isEqualTo(200);
        assertThat(row(pending)).isEqualTo(new Snapshot("READY", T0, 3));
        assertThat(count("attendance")).isEqualTo(1);
    }

    // ---- TC-16 -----------------------------------------------------------------------------------------------

    @Test
    void aGuestMarkedLeftReadsLeftAndAMemberMarkedLeftCannotJoinAgain() throws Exception {
        UUID game = insertGame("OPEN");
        UUID guest = attend(game, "PENDING");
        UUID member = attend(game, MEMBER_PLAYER, "READY");

        assertThat(hostSets(game, guest, "LEFT").statusCode()).isEqualTo(200);
        assertThat(hostSets(game, member, "LEFT").statusCode()).isEqualTo(200);

        HttpResponse<String> mine = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/games/" + game
                                + "/my-attendance?attendanceId=" + guest))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> join = http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/" + game + "/member-attendances"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"userId\":\"" + UUID.randomUUID() + "\",\"displayName\":\"Mem\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(JSON.readTree(mine.body()).get("attendanceState").asString()).isEqualTo("LEFT");
        assertConflict(join, "You were removed from this game.");
        assertThat(row(member).state()).isEqualTo("LEFT");
    }
}
