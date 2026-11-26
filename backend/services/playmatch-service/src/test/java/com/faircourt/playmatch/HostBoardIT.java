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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/host/games/{gameId}/board} against PostgreSQL, with player-service replaced by a JDK HTTP stub
 * and the caller given as a test identity token (Host board plan, TC-15 to TC-26).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.clean-disabled=false", "faircourt.services.read-timeout=400ms"})
class HostBoardIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final Instant T = Instant.parse("2026-11-25T12:30:00Z");
    private static final Instant BASE = Instant.parse("2026-11-25T10:00:00Z");

    /** A clock the test can set; it is the application's clock, so tokens and {@code asOf} follow it. */
    static final class TestClock extends Clock {
        volatile Instant now = T;

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
            return now;
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

    /** Stand-in for player-service: answers levels from a map; can fail or stall. */
    private static final class PlayerStub {
        final HttpServer server;
        final Map<UUID, String[]> levels = new ConcurrentHashMap<>();
        final Set<UUID> omit = ConcurrentHashMap.newKeySet();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        volatile int failStatus;
        volatile long stallMillis;

        PlayerStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            levels.clear();
            omit.clear();
            bodies.clear();
            failStatus = 0;
            stallMillis = 0;
        }

        private void handle(HttpExchange exchange) throws IOException {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(request);
            try {
                if (stallMillis > 0) {
                    Thread.sleep(stallMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int status = 200;
            StringBuilder body = new StringBuilder("{\"players\":[");
            if (failStatus != 0) {
                status = failStatus;
                body = new StringBuilder("{}");
            } else {
                boolean first = true;
                for (JsonNode id : JSON.readTree(request).get("playerIds")) {
                    UUID playerId = UUID.fromString(id.asString());
                    String[] level = levels.get(playerId);
                    if (level == null || omit.contains(playerId)) {
                        continue;
                    }
                    body.append(first ? "" : ",")
                            .append("{\"playerId\":\"")
                            .append(playerId)
                            .append("\",\"kind\":\"")
                            .append(level[0])
                            .append("\",\"band\":")
                            .append(level[1])
                            .append(",\"bandStatus\":\"")
                            .append(level[2])
                            .append("\"}");
                    first = false;
                }
                body.append("]}");
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static final PlayerStub PLAYER = new PlayerStub();

    @DynamicPropertySource
    static void playerServiceIsTheStub(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.player-url", PLAYER::url);
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
        PLAYER.reset();
        CLOCK.now = T;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static String boardPath(String gameId) {
        return "/api/v1/host/games/" + gameId + "/board";
    }

    private static String tokenFor(UUID user, String gameId) {
        return new IdentityToken(SECRET, CLOCK).issue(user, "playmatch-service", "GET", boardPath(gameId));
    }

    private HttpResponse<String> board(String gameId, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/host/games/" + gameId + "/board"));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private UUID insertGame(UUID hostUser, int courts, String format, String state) {
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

    /** A player known to the stub (kind, band as JSON text, status) and one attendance row of the game. */
    private record Row(UUID attendanceId, UUID playerId, String name, String state) {}

    private Row attend(
            UUID game,
            String name,
            String state,
            String kind,
            String band,
            String bandStatus,
            long availableMinutes,
            int matchesPlayed) {
        UUID player = UUID.randomUUID();
        PLAYER.levels.put(player, new String[] {kind, band, bandStatus});
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
				                        matches_played)
				VALUES (:id, :game, :player, :name, :state, :checkedIn, :available, :played)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("name", name)
                .param("state", state)
                .param("checkedIn", OffsetDateTime.ofInstant(BASE.minusSeconds(60), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(BASE.plusSeconds(availableMinutes * 60), ZoneOffset.UTC))
                .param("played", matchesPlayed)
                .update();
        return new Row(id, player, name, state);
    }

    private UUID insertMatch(UUID game, int matchNo, int court, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO play_match (id, game_id, match_no, court_no, state)
				VALUES (:id, :game, :no, :court, :state)""")
                .param("id", id)
                .param("game", game)
                .param("no", matchNo)
                .param("court", court)
                .param("state", state)
                .update();
        return id;
    }

    private void matchPlayer(UUID match, UUID game, UUID attendance, String team, boolean released) {
        jdbc.sql("""
				INSERT INTO match_player (match_id, game_id, attendance_id, team, released_at)
				VALUES (:match, :game, :attendance, :team, :released)""")
                .param("match", match)
                .param("game", game)
                .param("attendance", attendance)
                .param("team", team)
                .param("released", released ? OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC) : null)
                .update();
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asString());
        }
        return out;
    }

    // ---- TC-15, TC-24 ----------------------------------------------------------------------------------------

    /** The board of TC-15: 12 attendance rows, an ACTIVE doubles match on court 2. */
    private record Fixture(
            UUID game, Row guest, List<Row> ready, Row resting, Row left, List<Row> playing, UUID match) {}

    private Fixture doublesBoard() {
        UUID game = insertGame(host, 2, "DOUBLES", "OPEN");
        Row guest = attend(game, "Guest Mai", "PENDING", "GUEST", "null", "SELF_REPORTED", 5, 0);
        List<Row> ready = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ready.add(attend(game, "Ready " + i, "READY", "MEMBER", String.valueOf(1 + i % 4), "CONFIRMED", 10 + i, 1));
        }
        Row resting = attend(game, "Rest Rae", "RESTING", "MEMBER", "2", "OBSERVED", 20, 2);
        Row left = attend(game, "Left Lee", "LEFT", "MEMBER", "3", "CONFIRMED", 21, 2);
        List<Row> playing = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            playing.add(attend(game, "Play " + i, "PLAYING", "MEMBER", "3", "CONFIRMED", 30 + i, 0));
        }
        UUID match = insertMatch(game, 1, 2, "ACTIVE");
        matchPlayer(match, game, playing.get(0).attendanceId(), "A", false);
        matchPlayer(match, game, playing.get(1).attendanceId(), "A", false);
        matchPlayer(match, game, playing.get(2).attendanceId(), "B", false);
        matchPlayer(match, game, playing.get(3).attendanceId(), "B", false);
        return new Fixture(game, guest, ready, resting, left, playing, match);
    }

    @Test
    void theHostSeesEveryAttendanceRowWithLevelsAndTheActiveMatchOnItsCourt() throws Exception {
        Fixture f = doublesBoard();

        HttpResponse<String> response =
                board(f.game().toString(), tokenFor(host, f.game().toString()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        JsonNode body = json(response);
        assertThat(body.get("players")).hasSize(12);
        JsonNode guest = byAttendance(body, f.guest().attendanceId());
        assertThat(guest.get("state").asString()).isEqualTo("PENDING");
        assertThat(guest.get("displayName").asString()).isEqualTo("Guest Mai");
        assertThat(guest.get("kind").asString()).isEqualTo("GUEST");
        assertThat(guest.get("band").isNull()).isTrue();
        assertThat(guest.get("bandStatus").asString()).isEqualTo("SELF_REPORTED");
        assertThat(guest.get("matchesPlayed").asInt()).isZero();
        assertThat(Instant.parse(guest.get("checkedInAt").asString())).isEqualTo(BASE.minusSeconds(60));
        assertThat(Instant.parse(guest.get("availableSince").asString())).isEqualTo(BASE.plusSeconds(300));
        JsonNode ready2 = byAttendance(body, f.ready().get(2).attendanceId());
        assertThat(ready2.get("kind").asString()).isEqualTo("MEMBER");
        assertThat(ready2.get("band").asInt()).isEqualTo(3);
        assertThat(ready2.get("bandStatus").asString()).isEqualTo("CONFIRMED");
        assertThat(ready2.get("matchesPlayed").asInt()).isEqualTo(1);
        assertThat(byAttendance(body, f.resting().attendanceId()).get("state").asString())
                .isEqualTo("RESTING");
        assertThat(byAttendance(body, f.left().attendanceId()).get("state").asString())
                .isEqualTo("LEFT");
        for (Row p : f.playing()) {
            assertThat(byAttendance(body, p.attendanceId()).get("state").asString())
                    .isEqualTo("PLAYING");
        }
        JsonNode courts = body.get("courts");
        assertThat(courts).hasSize(2);
        assertThat(courts.get(0).get("courtNo").asInt()).isEqualTo(1);
        assertThat(courts.get(0).get("match").isNull()).isTrue();
        assertThat(courts.get(1).get("courtNo").asInt()).isEqualTo(2);
        JsonNode match = courts.get(1).get("match");
        assertThat(match.get("matchId").asString()).isEqualTo(f.match().toString());
        assertThat(match.get("matchNo").asInt()).isEqualTo(1);
        assertThat(match.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(ids(match.get("teams").get("A")))
                .containsExactlyInAnyOrder(
                        f.playing().get(0).attendanceId().toString(),
                        f.playing().get(1).attendanceId().toString());
        assertThat(ids(match.get("teams").get("B")))
                .containsExactlyInAnyOrder(
                        f.playing().get(2).attendanceId().toString(),
                        f.playing().get(3).attendanceId().toString());
        JsonNode game = body.get("game");
        assertThat(game.get("id").asString()).isEqualTo(f.game().toString());
        assertThat(game.get("courtCount").asInt()).isEqualTo(2);
        assertThat(game.get("state").asString()).isEqualTo("OPEN");
        assertThat(game.get("matchFormat").asString()).isEqualTo("DOUBLES");
        assertThat(game.get("minSets").asInt()).isEqualTo(6);
    }

    private static JsonNode byAttendance(JsonNode board, UUID attendanceId) {
        for (JsonNode p : board.get("players")) {
            if (p.get("attendanceId").asString().equals(attendanceId.toString())) {
                return p;
            }
        }
        throw new AssertionError("attendance not on the board: " + attendanceId);
    }

    @Test
    void theBoardAnswerHasExactlyTheDocumentedFieldsAndNoIdentityOfAnyone() throws Exception {
        Fixture f = doublesBoard();

        HttpResponse<String> response =
                board(f.game().toString(), tokenFor(host, f.game().toString()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonAllowlist.fieldPaths(response.body()))
                .isEqualTo(Set.of(
                        "asOf",
                        "game",
                        "game.id",
                        "game.publicCode",
                        "game.title",
                        "game.venue",
                        "game.startsAt",
                        "game.endsAt",
                        "game.courtCount",
                        "game.state",
                        "game.matchFormat",
                        "game.minSets",
                        "players",
                        "players[].attendanceId",
                        "players[].displayName",
                        "players[].kind",
                        "players[].state",
                        "players[].band",
                        "players[].bandStatus",
                        "players[].checkedInAt",
                        "players[].availableSince",
                        "players[].matchesPlayed",
                        "courts",
                        "courts[].courtNo",
                        "courts[].match",
                        "courts[].match.matchId",
                        "courts[].match.matchNo",
                        "courts[].match.state",
                        "courts[].match.teams",
                        "courts[].match.teams.A",
                        "courts[].match.teams.B"));
        assertThat(response.body()).doesNotContain(host.toString());
        Set<UUID> playerIds = new HashSet<>(PLAYER.levels.keySet());
        for (UUID playerId : playerIds) {
            assertThat(response.body()).doesNotContain(playerId.toString());
        }
    }

    // ---- TC-16, TC-17 ----------------------------------------------------------------------------------------

    @Test
    void aSinglesPlannedMatchHasOnePlayerPerTeamAndTheyAreAssigned() throws Exception {
        UUID game = insertGame(host, 2, "SINGLES", "OPEN");
        Row a = attend(game, "Ann", "ASSIGNED", "MEMBER", "2", "CONFIRMED", 1, 0);
        Row b = attend(game, "Bo", "ASSIGNED", "MEMBER", "2", "CONFIRMED", 2, 0);
        UUID match = insertMatch(game, 1, 1, "PLANNED");
        matchPlayer(match, game, a.attendanceId(), "A", false);
        matchPlayer(match, game, b.attendanceId(), "B", false);

        JsonNode body = json(board(game.toString(), tokenFor(host, game.toString())));

        JsonNode court = body.get("courts").get(0);
        assertThat(court.get("match").get("state").asString()).isEqualTo("PLANNED");
        assertThat(ids(court.get("match").get("teams").get("A")))
                .containsExactly(a.attendanceId().toString());
        assertThat(ids(court.get("match").get("teams").get("B")))
                .containsExactly(b.attendanceId().toString());
        assertThat(body.get("courts").get(1).get("match").isNull()).isTrue();
        assertThat(byAttendance(body, a.attendanceId()).get("state").asString()).isEqualTo("ASSIGNED");
        assertThat(byAttendance(body, b.attendanceId()).get("state").asString()).isEqualTo("ASSIGNED");
    }

    @Test
    void finishedAndCancelledMatchesAreNotShownAndReleasedPlayersAreNotInTeams() throws Exception {
        UUID game = insertGame(host, 1, "SINGLES", "OPEN");
        Row a = attend(game, "Ann", "READY", "MEMBER", "2", "CONFIRMED", 1, 1);
        Row b = attend(game, "Bo", "READY", "MEMBER", "2", "CONFIRMED", 2, 1);
        UUID finished = insertMatch(game, 1, 1, "FINISHED");
        matchPlayer(finished, game, a.attendanceId(), "A", true);
        matchPlayer(finished, game, b.attendanceId(), "B", true);
        insertMatch(game, 2, 1, "CANCELLED");

        JsonNode body = json(board(game.toString(), tokenFor(host, game.toString())));

        assertThat(body.get("courts")).hasSize(1);
        assertThat(body.get("courts").get(0).get("match").isNull()).isTrue();
    }

    // ---- TC-18, TC-19, TC-20 ---------------------------------------------------------------------------------

    @Test
    void playersAreOrderedByStateGroupThenMatchesPlayedThenAvailableSinceThenId() throws Exception {
        UUID game = insertGame(host, 1, "DOUBLES", "OPEN");
        Row left = attend(game, "L", "LEFT", "MEMBER", "1", "CONFIRMED", 1, 0);
        Row resting = attend(game, "Rs", "RESTING", "MEMBER", "1", "CONFIRMED", 1, 0);
        Row playing = attend(game, "P", "PLAYING", "MEMBER", "1", "CONFIRMED", 1, 0);
        Row assigned = attend(game, "As", "ASSIGNED", "MEMBER", "1", "CONFIRMED", 1, 0);
        Row readyLate0 = attend(game, "R-late-0", "READY", "MEMBER", "1", "CONFIRMED", 50, 0);
        Row readyEarly1 = attend(game, "R-early-1", "READY", "MEMBER", "1", "CONFIRMED", 5, 1);
        Row readyEarly0 = attend(game, "R-early-0", "READY", "MEMBER", "1", "CONFIRMED", 5, 0);
        Row pending = attend(game, "Pe", "PENDING", "GUEST", "null", "SELF_REPORTED", 1, 0);

        JsonNode body = json(board(game.toString(), tokenFor(host, game.toString())));

        List<String> names = new ArrayList<>();
        for (JsonNode p : body.get("players")) {
            names.add(p.get("displayName").asString());
        }
        assertThat(names)
                .containsExactly(
                        pending.name(),
                        readyEarly0.name(),
                        readyLate0.name(),
                        readyEarly1.name(),
                        assigned.name(),
                        playing.name(),
                        resting.name(),
                        left.name());
    }

    @Test
    void asOfIsTheServerClockInIsoUtc() throws Exception {
        UUID game = insertGame(host, 1, "DOUBLES", "OPEN");

        JsonNode body = json(board(game.toString(), tokenFor(host, game.toString())));

        assertThat(body.get("asOf").asString()).isEqualTo("2026-11-25T12:30:00Z");
        assertThat(body.get("game").get("startsAt").asString()).isEqualTo("2026-11-25T10:00:00Z");
        assertThat(body.get("players")).isEmpty();
    }

    @Test
    void aClosedGameStillShowsItsBoard() throws Exception {
        UUID game = insertGame(host, 1, "DOUBLES", "CLOSED");

        HttpResponse<String> response = board(game.toString(), tokenFor(host, game.toString()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("game").get("state").asString()).isEqualTo("CLOSED");
    }

    // ---- TC-21, TC-22, TC-23 ---------------------------------------------------------------------------------

    @Test
    void anotherAccountGets403WithOnlyTheErrorFieldsAndTheStubIsNotCalled() throws Exception {
        Fixture f = doublesBoard();

        HttpResponse<String> response =
                board(f.game().toString(), tokenFor(UUID.randomUUID(), f.game().toString()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(JsonAllowlist.fieldPaths(response.body())).isEqualTo(Set.of("code", "message", "requestId"));
        assertThat(PLAYER.bodies).isEmpty();
    }

    @Test
    void anUnknownOrMalformedGameIs404ForTheHost() throws Exception {
        for (String game : new String[] {UUID.randomUUID().toString(), "not-a-uuid"}) {
            HttpResponse<String> response = board(game, tokenFor(host, game));

            assertThat(response.statusCode()).as(game).isEqualTo(404);
            assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
        }
        assertThat(PLAYER.bodies).isEmpty();
    }

    @Test
    void noTokenAnExpiredTokenOrATokenOfAnotherSecretIs401EvenForAnUnknownGame() throws Exception {
        Fixture f = doublesBoard();
        Clock past = Clock.fixed(T.minusSeconds(60), ZoneOffset.UTC);

        for (String game : new String[] {f.game().toString(), UUID.randomUUID().toString(), "not-a-uuid"}) {
            String path = boardPath(game);
            String expired = new IdentityToken(SECRET, past).issue(host, "playmatch-service", "GET", path);
            String otherSecret = new IdentityToken("another-secret-of-more-than-32-bytes!!", CLOCK)
                    .issue(host, "playmatch-service", "GET", path);
            String tampered = tokenFor(host, game) + "x";
            for (String token : new String[] {null, expired, otherSecret, tampered}) {
                HttpResponse<String> response = board(game, token);

                assertThat(response.statusCode()).as(game + " " + token).isEqualTo(401);
                assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
            }
        }
        assertThat(PLAYER.bodies).isEmpty();
    }

    // ---- TC-25, TC-26 ----------------------------------------------------------------------------------------

    @Test
    void whenPlayerServiceFailsOrDoesNotAnswerInTimeTheAnswerIs503WithNoBoard() throws Exception {
        Fixture f = doublesBoard();

        PLAYER.failStatus = 500;
        HttpResponse<String> failed =
                board(f.game().toString(), tokenFor(host, f.game().toString()));
        PLAYER.failStatus = 0;
        PLAYER.stallMillis = 1500;
        HttpResponse<String> slow =
                board(f.game().toString(), tokenFor(host, f.game().toString()));

        for (HttpResponse<String> response : List.of(failed, slow)) {
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
            assertThat(JsonAllowlist.fieldPaths(response.body())).isEqualTo(Set.of("code", "message", "requestId"));
        }
    }

    @Test
    void aPlayerMissingFromTheLevelsAnswerShowsNullsAndLogsOnlyTheAttendanceId(CapturedOutput output) throws Exception {
        UUID game = insertGame(host, 1, "DOUBLES", "OPEN");
        Row known = attend(game, "Known Kim", "READY", "MEMBER", "2", "CONFIRMED", 1, 0);
        Row unknown = attend(game, "Zorbulon Zed", "READY", "MEMBER", "2", "CONFIRMED", 2, 0);
        PLAYER.omit.add(unknown.playerId());

        HttpResponse<String> response = board(game.toString(), tokenFor(host, game.toString()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode body = json(response);
        JsonNode missing = byAttendance(body, unknown.attendanceId());
        assertThat(missing.get("kind").isNull()).isTrue();
        assertThat(missing.get("band").isNull()).isTrue();
        assertThat(missing.get("bandStatus").isNull()).isTrue();
        assertThat(missing.get("displayName").asString()).isEqualTo("Zorbulon Zed");
        assertThat(byAttendance(body, known.attendanceId()).get("band").asInt()).isEqualTo(2);
        assertThat(output.getAll()).contains(unknown.attendanceId().toString());
        assertThat(output.getAll()).doesNotContain("Zorbulon", "Known Kim");
        assertThat(output.getAll().lines().filter(l -> l.contains("No level from player-service")))
                .hasSize(1);
    }
}
