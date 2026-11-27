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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Own assignment in {@code GET /internal/v1/games/{gameId}/my-attendance} (My game design sections 3 to 5; plan TC-01 to
 * TC-06 and TC-09). Matches are made and moved through the real host routes with test identity tokens; player-service
 * is a JDK HTTP stub (levels and member lookup).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MyAssignmentIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final String SERVICE = "playmatch-service";
    private static final Instant T = Instant.parse("2026-11-27T12:30:00Z");
    private static final Instant BASE = Instant.parse("2026-11-27T10:00:00Z");
    private static final String ALLOWED_A = "attendanceState";
    private static final String[] ALLOWED = {
        ALLOWED_A, "assignment", "assignment.matchNo", "assignment.courtNo", "assignment.matchState"
    };

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

    /** userId to player id, for the member lookup. */
    private static final Map<String, UUID> MEMBERS = new ConcurrentHashMap<>();

    private static final HttpServer PLAYER = startPlayerStub();

    private static HttpServer startPlayerStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", MyAssignmentIT::answerPlayer);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void answerPlayer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int status = 200;
        String body;
        if (path.startsWith("/internal/v1/players/by-user/")) {
            UUID id = MEMBERS.get(path.substring("/internal/v1/players/by-user/".length()));
            if (id == null) {
                status = 404;
                body = "{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"requestId\":\"x\"}";
            } else {
                body = "{\"playerId\":\"" + id + "\"}";
            }
        } else {
            StringBuilder sb = new StringBuilder("{\"players\":[");
            boolean first = true;
            JsonNode ids = request.isEmpty() ? null : JSON.readTree(request).get("playerIds");
            if (ids != null) {
                for (JsonNode id : ids) {
                    sb.append(first ? "" : ",")
                            .append("{\"playerId\":\"")
                            .append(id.asString())
                            .append("\",\"kind\":\"MEMBER\",\"band\":2,\"bandStatus\":\"CONFIRMED\"}");
                    first = false;
                }
            }
            body = sb.append("]}").toString();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
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
        MEMBERS.clear();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private String token(String method, String path) {
        return new IdentityToken(SECRET, CLOCK).issue(host, SERVICE, method, path);
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

    private UUID confirm(UUID game, int court, List<UUID> four) throws Exception {
        String path = "/api/v1/host/games/" + game + "/matches";
        String body = "{\"courtNo\":" + court + ",\"teams\":{\"A\":" + ids(four.subList(0, 2)) + ",\"B\":"
                + ids(four.subList(2, 4)) + "}}";
        HttpResponse<String> response = send("POST", path, token("POST", path), body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(JSON.readTree(response.body()).get("matchId").asString());
    }

    private void action(UUID match, String action) throws Exception {
        String path = "/api/v1/host/matches/" + match + "/" + action;
        HttpResponse<String> response = send("PATCH", path, token("PATCH", path), "");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    private static String ids(List<UUID> ids) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : ids) {
            quoted.add("\"" + id + "\"");
        }
        return "[" + String.join(",", quoted) + "]";
    }

    private HttpResponse<String> mine(UUID game, String query) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/" + game + "/my-attendance" + query))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String ofAttendance(UUID game, UUID attendance) throws Exception {
        HttpResponse<String> response = mine(game, "?attendanceId=" + attendance);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), ALLOWED);
        return response.body();
    }

    private static String assigned(String state, String matchState, int matchNo, int courtNo) {
        return "{\"attendanceState\":\"" + state + "\",\"assignment\":{\"matchNo\":" + matchNo + ",\"courtNo\":"
                + courtNo + ",\"matchState\":\"" + matchState + "\"}}";
    }

    private static final String WAITING = "{\"attendanceState\":\"READY\",\"assignment\":null}";

    private UUID insertGame(int courts) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
						INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state,
						                  match_format, min_sets)
						VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, :courts, 'OPEN',
						        'DOUBLES', 6)""")
                .param("id", id)
                .param("code", id.toString().substring(0, 8).toUpperCase())
                .param("host", host)
                .param("startsAt", OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(BASE.plusSeconds(7200), ZoneOffset.UTC))
                .param("courts", courts)
                .update();
        return id;
    }

    private UUID attend(UUID game, UUID player) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
						INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
						                        matches_played)
						VALUES (:id, :game, :player, :name, 'READY', :checkedIn, :available, 0)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("name", "Zorbulon " + id.toString().substring(0, 4))
                .param("checkedIn", OffsetDateTime.ofInstant(BASE.minusSeconds(60), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(BASE.plusSeconds(300), ZoneOffset.UTC))
                .update();
        return id;
    }

    private List<UUID> ready(UUID game, int count) {
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(attend(game, UUID.randomUUID()));
        }
        return list;
    }

    // ---- tests -----------------------------------------------------------------------------------------------

    @Test
    void eachOfTheFourConfirmedPlayersSeesOwnMatchAndTheFifthSeesNone() throws Exception {
        UUID game = insertGame(1);
        List<UUID> p = ready(game, 5);
        confirm(game, 1, p.subList(0, 4));

        for (int i = 0; i < 4; i++) {
            assertThat(ofAttendance(game, p.get(i))).isEqualTo(assigned("ASSIGNED", "PLANNED", 1, 1));
        }
        assertThat(ofAttendance(game, p.get(4))).isEqualTo(WAITING);
    }

    @Test
    void afterStartThePlayersArePlayingInAnActiveMatch() throws Exception {
        UUID game = insertGame(1);
        List<UUID> p = ready(game, 4);
        UUID match = confirm(game, 1, p);

        action(match, "start");

        for (UUID player : p) {
            assertThat(ofAttendance(game, player)).isEqualTo(assigned("PLAYING", "ACTIVE", 1, 1));
        }
    }

    @Test
    void afterFinishOrCancelThePlayersAreReadyWithNoAssignment() throws Exception {
        UUID game = insertGame(3);
        List<UUID> p = ready(game, 12);
        UUID finished = confirm(game, 1, p.subList(0, 4));
        UUID plannedCancelled = confirm(game, 2, p.subList(4, 8));
        UUID activeCancelled = confirm(game, 3, p.subList(8, 12));
        action(finished, "start");
        action(finished, "finish");
        action(plannedCancelled, "cancel");
        action(activeCancelled, "start");
        action(activeCancelled, "cancel");

        for (UUID player : p) {
            assertThat(ofAttendance(game, player)).isEqualTo(WAITING);
        }
    }

    @Test
    void aGuestByAttendanceIdAndAMemberByUserIdEachGetTheirOwnAssignment() throws Exception {
        UUID game = insertGame(1);
        UUID memberUser = UUID.randomUUID();
        UUID memberPlayer = UUID.randomUUID();
        MEMBERS.put(memberUser.toString(), memberPlayer);
        UUID guest1 = attend(game, UUID.randomUUID());
        UUID guest2 = attend(game, UUID.randomUUID());
        UUID guest3 = attend(game, UUID.randomUUID());
        UUID member = attend(game, memberPlayer);
        confirm(game, 1, List.of(guest1, guest2, guest3, member));

        HttpResponse<String> asMember = mine(game, "?userId=" + memberUser);

        assertThat(ofAttendance(game, guest1)).isEqualTo(assigned("ASSIGNED", "PLANNED", 1, 1));
        assertThat(asMember.statusCode()).as(asMember.body()).isEqualTo(200);
        assertThat(asMember.body()).isEqualTo(assigned("ASSIGNED", "PLANNED", 1, 1));
        JsonAllowlist.assertOnlyFields(asMember.body(), ALLOWED);
    }

    @Test
    void aPlayerOnCourtTwoSeesCourtTwoAndItsMatchNumberNeverTheOtherMatch() throws Exception {
        UUID game = insertGame(2);
        List<UUID> p = ready(game, 8);
        confirm(game, 2, p.subList(0, 4));
        confirm(game, 1, p.subList(4, 8));

        assertThat(ofAttendance(game, p.get(0))).isEqualTo(assigned("ASSIGNED", "PLANNED", 1, 2));
        assertThat(ofAttendance(game, p.get(3))).isEqualTo(assigned("ASSIGNED", "PLANNED", 1, 2));
        assertThat(ofAttendance(game, p.get(4))).isEqualTo(assigned("ASSIGNED", "PLANNED", 2, 1));
        assertThat(ofAttendance(game, p.get(7))).isEqualTo(assigned("ASSIGNED", "PLANNED", 2, 1));
    }

    @Test
    void theAnswerHasOnlyTheAllowedKeysWithAndWithoutAnAssignment() throws Exception {
        UUID game = insertGame(1);
        List<UUID> p = ready(game, 5);
        UUID match = confirm(game, 1, p.subList(0, 4));

        String planned = ofAttendance(game, p.get(0));
        String waiting = ofAttendance(game, p.get(4));
        action(match, "start");
        String active = ofAttendance(game, p.get(0));

        assertThat(JsonAllowlist.fieldPaths(planned)).contains("assignment.matchNo", "assignment.matchState");
        assertThat(JsonAllowlist.fieldPaths(active)).contains("assignment.courtNo");
        assertThat(JsonAllowlist.fieldPaths(waiting)).doesNotContain("assignment.matchNo");
    }

    @Test
    void anotherGamesMatchIsNeverReadAndAPlayerWithNoRowInTheGameGets404() throws Exception {
        UUID gameX = insertGame(1);
        UUID gameY = insertGame(1);
        UUID player = UUID.randomUUID();
        UUID userOfPlayer = UUID.randomUUID();
        MEMBERS.put(userOfPlayer.toString(), player);
        UUID inX = attend(gameX, player);
        confirm(gameY, 1, ready(gameY, 4));

        HttpResponse<String> byAttendance = mine(gameY, "?attendanceId=" + inX);
        HttpResponse<String> byUser = mine(gameY, "?userId=" + userOfPlayer);

        for (HttpResponse<String> response : List.of(byAttendance, byUser)) {
            assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("assignment");
        }
        assertThat(ofAttendance(gameX, inX)).isEqualTo(WAITING);
    }
}
