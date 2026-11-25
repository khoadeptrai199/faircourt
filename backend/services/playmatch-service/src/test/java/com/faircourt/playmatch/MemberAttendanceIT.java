package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Member join and own-attendance read, playmatch-service side (My attendance design sections 3 to 5; AD-16): {@code
 * POST /internal/v1/games/{gameId}/member-attendances} and {@code GET /internal/v1/games/{gameId}/my-attendance}
 * against PostgreSQL, with player-service replaced by a JDK HTTP stub. Covers TC-01 to TC-07, TC-10 and TC-11 of the
 * plan.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.clean-disabled=false"})
class MemberAttendanceIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern USER_ID = Pattern.compile("\"userId\":\"([^\"]+)\"");
    private static final Pattern DISPLAY_NAME = Pattern.compile("\"displayName\":\"([^\"]*)\"");

    /** Stand-in for player-service: one profile per account, like the real find-or-create. */
    private static final class PlayerStub {
        HttpServer server;
        final int fixedPort;
        final List<String> bodies = new CopyOnWriteArrayList<>();
        final Map<String, UUID> profiles = new ConcurrentHashMap<>();
        final Map<String, String> names = new ConcurrentHashMap<>();
        volatile int failStatus = 0;
        volatile boolean down;
        /** Runs while the find-or-create call is being answered, i.e. between playmatch's two game checks. */
        volatile Runnable onMembers;

        PlayerStub() {
            start(0);
            fixedPort = server.getAddress().getPort();
        }

        private void start(int bindPort) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", bindPort), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        void down() {
            server.stop(0);
            down = true;
        }

        void reset() {
            if (down) {
                start(fixedPort);
                down = false;
            }
            failStatus = 0;
            bodies.clear();
            profiles.clear();
            names.clear();
            onMembers = null;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(request);
            int status = 200;
            String body;
            if (failStatus != 0) {
                status = failStatus;
                body = "{}";
            } else if (path.equals("/internal/v1/players/members")) {
                Matcher m = USER_ID.matcher(request);
                String user = m.find() ? m.group(1) : "";
                UUID id = profiles.computeIfAbsent(user, k -> UUID.randomUUID());
                Matcher n = DISPLAY_NAME.matcher(request);
                String given = n.find() ? n.group(1) : "";
                // Like the real find-or-create: the given name is kept only for a profile created now.
                String name = names.computeIfAbsent(user, k -> given);
                if (onMembers != null) {
                    onMembers.run();
                }
                body = "{\"playerId\":\"" + id + "\",\"displayName\":\"" + name + "\"}";
            } else if (path.startsWith("/internal/v1/players/by-user/")) {
                UUID id = profiles.get(path.substring("/internal/v1/players/by-user/".length()));
                if (id == null) {
                    status = 404;
                    body = "{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"requestId\":\"x\"}";
                } else {
                    body = "{\"playerId\":\"" + id + "\"}";
                }
            } else {
                status = 404;
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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
    private UUID gameId;

    @BeforeEach
    void resetAndInsertAnOpenGame() {
        TestDatabase.reset(jdbc, flyway);
        PLAYER.reset();
        gameId = insertGame("OPENGAME", "OPEN");
    }

    private UUID insertGame(String code, String state) {
        UUID id = UUID.randomUUID();
        Instant endsAt = Instant.now().plusSeconds(3600);
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state)
				VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, 2, :state)""")
                .param("id", id)
                .param("code", code)
                .param("host", UUID.randomUUID())
                .param("startsAt", OffsetDateTime.ofInstant(endsAt.minusSeconds(7200), ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC))
                .param("state", state)
                .update();
        return id;
    }

    private HttpResponse<String> join(String game, UUID userId, String name) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/" + game + "/member-attendances"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"userId\":\"" + userId + "\",\"displayName\":\"" + name + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> mine(String game, String query) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/" + game + "/my-attendance" + query))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private long attendanceCount() {
        return jdbc.sql("SELECT count(*) FROM attendance").query(Long.class).single();
    }

    private UUID insertAttendance(UUID game, UUID player, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state)
				VALUES (:id, :game, :player, 'Someone', :state)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("state", state)
                .update();
        return id;
    }

    @Test
    void aMemberJoinsAnOpenGameAsReadyWithOneRowAndTheGivenName() throws Exception {
        UUID userId = UUID.randomUUID();

        HttpResponse<String> response = join(gameId.toString(), userId, "  host  ");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), "attendanceState", "created");
        JsonNode answer = JSON.readTree(response.body());
        assertThat(answer.get("attendanceState").asString()).isEqualTo("READY");
        assertThat(answer.get("created").asBoolean()).isTrue();
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        Map<String, Object> row = jdbc.sql("SELECT game_id, player_id, display_name, state FROM attendance")
                .query()
                .singleRow();
        assertThat(row)
                .containsEntry("game_id", gameId)
                .containsEntry("player_id", PLAYER.profiles.get(userId.toString()))
                .containsEntry("display_name", "host")
                .containsEntry("state", "READY");
    }

    @Test
    void anExistingProfileKeepsItsOwnNameInTheAttendanceRowWhateverNameTheAccountGives() throws Exception {
        UUID userId = UUID.randomUUID();
        PLAYER.profiles.put(userId.toString(), UUID.randomUUID());
        PLAYER.names.put(userId.toString(), "Player 3");

        HttpResponse<String> response = join(gameId.toString(), userId, "email-name");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT display_name FROM attendance")
                        .query(String.class)
                        .single())
                .isEqualTo("Player 3");
    }

    @Test
    void aNameOfEightyCodePointsMadeOfSurrogatePairsIsAcceptedAndStoredWhole() throws Exception {
        String name = "😀".repeat(80);

        HttpResponse<String> response = join(gameId.toString(), UUID.randomUUID(), name);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT display_name FROM attendance")
                        .query(String.class)
                        .single())
                .isEqualTo(name);
        assertThat(join(gameId.toString(), UUID.randomUUID(), name + "😀").statusCode())
                .isEqualTo(400);
    }

    @Test
    void aGameThatClosesAfterTheFirstCheckButBeforeTheInsertIs409AndGetsNoRow() throws Exception {
        // The player stub is called after playmatch's first (unlocked) game check and before its insert transaction.
        // Closing the game inside that call reproduces the race without any hook in production code.
        PLAYER.onMembers = () -> jdbc.sql("UPDATE game SET state = 'CLOSED' WHERE id = :id")
                .param("id", gameId)
                .update();

        HttpResponse<String> response = join(gameId.toString(), UUID.randomUUID(), "host");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(attendanceCount()).isZero();
    }

    @Test
    void joiningAgainReturnsTheSameRowWithCreatedFalseAndNothingChanges() throws Exception {
        UUID userId = UUID.randomUUID();
        join(gameId.toString(), userId, "host");
        Map<String, Object> before = jdbc.sql("SELECT id, available_since, checked_in_at FROM attendance")
                .query()
                .singleRow();

        HttpResponse<String> again = join(gameId.toString(), userId, "host");

        assertThat(again.statusCode()).isEqualTo(200);
        JsonNode answer = JSON.readTree(again.body());
        assertThat(answer.get("attendanceState").asString()).isEqualTo("READY");
        assertThat(answer.get("created").asBoolean()).isFalse();
        assertThat(attendanceCount()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT id, available_since, checked_in_at FROM attendance")
                        .query()
                        .singleRow())
                .isEqualTo(before);
    }

    @Test
    void anExistingStateOtherThanLeftIsReturnedAsIs() throws Exception {
        UUID userId = UUID.randomUUID();
        join(gameId.toString(), userId, "host");
        jdbc.sql("UPDATE attendance SET state = 'RESTING'").update();

        HttpResponse<String> again = join(gameId.toString(), userId, "host");

        assertThat(again.statusCode()).isEqualTo(200);
        JsonNode answer = JSON.readTree(again.body());
        assertThat(answer.get("attendanceState").asString()).isEqualTo("RESTING");
        assertThat(answer.get("created").asBoolean()).isFalse();
    }

    @Test
    void twoParallelJoinsMakeOneRowAndExactlyOneCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        // The profile exists already, so both calls race only on the attendance insert.
        PLAYER.profiles.put(userId.toString(), UUID.randomUUID());
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                calls.add(() -> {
                    start.await();
                    HttpResponse<String> response = join(gameId.toString(), userId, "host");
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                    return JSON.readTree(response.body()).get("created").asBoolean();
                });
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> call : calls) {
                futures.add(pool.submit(call));
            }
            start.countDown();

            long created = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    created++;
                }
            }

            assertThat(attendanceCount()).isEqualTo(1);
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aClosedGameIs409StateConflictAndNothingIsCalledOrWritten() throws Exception {
        UUID closed = insertGame("CLOSED01", "CLOSED");
        long before = attendanceCount();

        HttpResponse<String> response = join(closed.toString(), UUID.randomUUID(), "host");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(attendanceCount()).isEqualTo(before);
        assertThat(PLAYER.bodies).isEmpty();
    }

    @Test
    void aMemberWhoseEntryIsLeftGets409AndTheRowStaysLeft() throws Exception {
        UUID userId = UUID.randomUUID();
        join(gameId.toString(), userId, "host");
        jdbc.sql("UPDATE attendance SET state = 'LEFT'").update();

        HttpResponse<String> response = join(gameId.toString(), userId, "host");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(jdbc.sql("SELECT state FROM attendance").query(String.class).list())
                .containsExactly("LEFT");
    }

    @Test
    void anUnknownOrMalformedGameIs404ForJoinAndForMyAttendanceAndNothingIsWritten() throws Exception {
        for (String game : new String[] {UUID.randomUUID().toString(), "not-a-uuid"}) {
            HttpResponse<String> join = join(game, UUID.randomUUID(), "host");
            HttpResponse<String> mine = mine(game, "?userId=" + UUID.randomUUID());

            assertThat(join.statusCode()).as("join " + game).isEqualTo(404);
            assertThat(join.body()).contains("\"code\":\"NOT_FOUND\"");
            assertThat(mine.statusCode()).as("mine " + game).isEqualTo(404);
        }
        assertThat(attendanceCount()).isZero();
        // Reading never creates a profile (only the join does, and it stopped at the unknown game).
        assertThat(PLAYER.profiles).isEmpty();
    }

    @Test
    void whenPlayerServiceFailsTheAnswerIs503AndNoRowIsWritten() throws Exception {
        PLAYER.failStatus = 500;

        HttpResponse<String> response = join(gameId.toString(), UUID.randomUUID(), "host");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(attendanceCount()).isZero();
    }

    @Test
    void whenPlayerServiceIsUnreachableTheAnswerIs503AndNoRowIsWritten() throws Exception {
        PLAYER.down();

        HttpResponse<String> response = join(gameId.toString(), UUID.randomUUID(), "host");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(attendanceCount()).isZero();
    }

    @Test
    void whenPlayerServiceIsDownMyAttendanceOfAMemberIs503ButAGuestsOwnRowStillReads() throws Exception {
        UUID guestAttendance = insertAttendance(gameId, UUID.randomUUID(), "PENDING");
        PLAYER.down();

        HttpResponse<String> member = mine(gameId.toString(), "?userId=" + UUID.randomUUID());
        HttpResponse<String> guest = mine(gameId.toString(), "?attendanceId=" + guestAttendance);

        assertThat(member.statusCode()).isEqualTo(503);
        assertThat(guest.statusCode()).isEqualTo(200);
    }

    @Test
    void badNamesAndMissingFieldsAre400AndNothingIsCalledOrWritten() throws Exception {
        // Name vectors: keep identical to the gateway and player-service tests.
        for (String name : new String[] {"", "   ", "A".repeat(81), "Ma\\u0007i", "Ma\\u0009i"}) {
            assertThat(join(gameId.toString(), UUID.randomUUID(), name).statusCode())
                    .as(name)
                    .isEqualTo(400);
        }
        HttpResponse<String> noUser = http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/" + gameId + "/member-attendances"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"host\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(noUser.statusCode()).isEqualTo(400);
        assertThat(attendanceCount()).isZero();
        assertThat(PLAYER.bodies).isEmpty();
    }

    @Test
    void myAttendanceByUserAndByAttendanceReturnOnlyTheOwnState() throws Exception {
        UUID member = UUID.randomUUID();
        join(gameId.toString(), member, "host");
        UUID guestAttendance = insertAttendance(gameId, UUID.randomUUID(), "PENDING");

        HttpResponse<String> asMember = mine(gameId.toString(), "?userId=" + member);
        HttpResponse<String> asGuest = mine(gameId.toString(), "?attendanceId=" + guestAttendance);

        assertThat(asMember.statusCode()).as(asMember.body()).isEqualTo(200);
        assertThat(asMember.body()).isEqualTo("{\"attendanceState\":\"READY\"}");
        JsonAllowlist.assertOnlyFields(asMember.body(), "attendanceState");
        assertThat(asMember.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(asGuest.statusCode()).isEqualTo(200);
        assertThat(asGuest.body()).isEqualTo("{\"attendanceState\":\"PENDING\"}");
        JsonAllowlist.assertOnlyFields(asGuest.body(), "attendanceState");
    }

    @Test
    void aRowThatIsNotTheCallersInThatGameIs404() throws Exception {
        UUID otherGame = insertGame("OTHER001", "OPEN");
        UUID guestOfOtherGame = insertAttendance(otherGame, UUID.randomUUID(), "READY");
        UUID member = UUID.randomUUID();
        join(otherGame.toString(), member, "host");
        UUID neverJoined = UUID.randomUUID();
        PLAYER.profiles.put(neverJoined.toString(), UUID.randomUUID());

        assertThat(mine(gameId.toString(), "?attendanceId=" + guestOfOtherGame).statusCode())
                .isEqualTo(404);
        assertThat(mine(gameId.toString(), "?attendanceId=" + UUID.randomUUID()).statusCode())
                .isEqualTo(404);
        // A member of another game, an account that has a profile but no entry, and one with no profile at all.
        assertThat(mine(gameId.toString(), "?userId=" + member).statusCode()).isEqualTo(404);
        assertThat(mine(gameId.toString(), "?userId=" + neverJoined).statusCode())
                .isEqualTo(404);
        assertThat(mine(gameId.toString(), "?userId=" + UUID.randomUUID()).statusCode())
                .isEqualTo(404);
        // The same people do read their own row in their own game.
        assertThat(mine(otherGame.toString(), "?attendanceId=" + guestOfOtherGame)
                        .statusCode())
                .isEqualTo(200);
        assertThat(mine(otherGame.toString(), "?userId=" + member).statusCode()).isEqualTo(200);
    }

    @Test
    void bothParametersNoParameterOrABadIdIs400() throws Exception {
        UUID id = UUID.randomUUID();

        for (String query : new String[] {
            "", "?userId=" + id + "&attendanceId=" + id, "?userId=not-a-uuid", "?attendanceId=not-a-uuid"
        }) {
            HttpResponse<String> response = mine(gameId.toString(), query);

            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        }
    }

    @Test
    void neitherTheNameNorTheEmailLikeTextAppearInTheLog(CapturedOutput output) throws Exception {
        join(gameId.toString(), UUID.randomUUID(), "Zorbulon");
        PLAYER.failStatus = 500;
        join(gameId.toString(), UUID.randomUUID(), "Quuxmire");
        PLAYER.failStatus = 0;
        mine(gameId.toString(), "?userId=" + UUID.randomUUID());

        assertThat(output.getAll()).doesNotContain("Zorbulon", "Quuxmire");
    }
}
