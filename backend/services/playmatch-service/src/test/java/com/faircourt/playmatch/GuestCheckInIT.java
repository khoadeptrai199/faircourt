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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
 * Guest check-in orchestration (Guest check-in design section 4; AD-16): {@code POST
 * /internal/v1/games/by-code/{publicCode}/guest-check-ins} against PostgreSQL, with player-service and auth-service
 * replaced by JDK HTTP stubs. Covers the happy path, 404 / 409, the same-browser case, and both failure paths (nothing
 * written when the player stub is down; the attendance withdrawn when the auth stub fails).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.clean-disabled=false"})
class GuestCheckInIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOKEN = "T".repeat(43);

    /** A downstream service stand-in that records requests and answers a settable status. */
    private static final class Stub {
        HttpServer server;
        int fixedPort;
        final List<String> bodies = new CopyOnWriteArrayList<>();
        volatile int status = 201;
        volatile boolean down;
        final Supplier<String> answer;

        Stub(Supplier<String> answer) {
            this.answer = answer;
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
            server.start();
        }

        /** Stops listening on the same port, so the next call is refused (connection refused). */
        void down() {
            server.stop(0);
            down = true;
        }

        private void handle(HttpExchange exchange) throws IOException {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset(int status) {
            if (down) {
                start(fixedPort);
                down = false;
            }
            this.status = status;
            bodies.clear();
        }
    }

    private static final AtomicReference<UUID> LAST_PLAYER_ID = new AtomicReference<>();
    private static final Stub PLAYER = new Stub(() -> {
        UUID id = UUID.randomUUID();
        LAST_PLAYER_ID.set(id);
        return "{\"playerId\":\"" + id + "\"}";
    });
    private static final Stub AUTH =
            new Stub(() -> "{\"token\":\"" + TOKEN + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");

    @DynamicPropertySource
    static void stubsAreTheServices(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.player-url", PLAYER::url);
        registry.add("faircourt.services.auth-url", AUTH::url);
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
        PLAYER.reset(201);
        AUTH.reset(201);
        gameId = insertGame("OPENGAME", "OPEN", Instant.now().plusSeconds(3600));
    }

    private UUID insertGame(String code, String state, Instant endsAt) {
        UUID id = UUID.randomUUID();
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

    private HttpResponse<String> checkIn(String code, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + port + "/internal/v1/games/by-code/" + code + "/guest-check-ins"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String body(String name, UUID existing) {
        return "{\"displayName\":\"" + name + "\",\"answers\":{\"serve\":2,\"rally\":1,\"movement\":1,"
                + "\"positioning\":1},\"existingAttendanceId\":" + (existing == null ? "null" : "\"" + existing + "\"")
                + "}";
    }

    private long attendanceCount() {
        return jdbc.sql("SELECT count(*) FROM attendance").query(Long.class).single();
    }

    private UUID insertAttendance(UUID game, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state)
				VALUES (:id, :game, :player, 'Someone', :state)""")
                .param("id", id)
                .param("game", game)
                .param("player", UUID.randomUUID())
                .param("state", state)
                .update();
        return id;
    }

    @Test
    void anOpenGameGetsAPendingAttendanceAndACredentialForTheGateway() throws Exception {
        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(
                response.body(),
                "attendanceState",
                "created",
                "credential",
                "credential.token",
                "credential.expiresAt");
        JsonNode answer = JSON.readTree(response.body());
        assertThat(answer.get("attendanceState").asString()).isEqualTo("PENDING");
        assertThat(answer.get("created").asBoolean()).isTrue();
        assertThat(answer.get("credential").get("token").asString()).isEqualTo(TOKEN);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        var row = jdbc.sql("SELECT game_id, player_id, display_name, state FROM attendance")
                .query()
                .singleRow();
        assertThat(row)
                .containsEntry("game_id", gameId)
                .containsEntry("player_id", LAST_PLAYER_ID.get())
                .containsEntry("display_name", "Mai")
                .containsEntry("state", "PENDING");
        assertThat(PLAYER.bodies).hasSize(1);
        assertThat(AUTH.bodies).hasSize(1);
        assertThat(AUTH.bodies.getFirst()).contains(gameId.toString());
    }

    @Test
    void theCredentialEndsTwelveHoursAfterTheGameEnds() throws Exception {
        checkIn("OPENGAME", body("Mai", null));

        JsonNode sent = JSON.readTree(AUTH.bodies.getFirst());
        Instant expiresAt = Instant.parse(sent.get("expiresAt").asString());
        Instant endsAt = jdbc.sql("SELECT ends_at FROM game WHERE id = :id")
                .param("id", gameId)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
        assertThat(expiresAt).isEqualTo(endsAt.plusSeconds(12 * 3600));
    }

    @Test
    void aGameThatAlreadyEndedStillGivesTheCredentialTwelveHoursFromNow() throws Exception {
        insertGame("ENDED001", "OPEN", Instant.now().minusSeconds(3600));

        checkIn("ENDED001", body("Mai", null));

        Instant expiresAt = Instant.parse(
                JSON.readTree(AUTH.bodies.getFirst()).get("expiresAt").asString());
        assertThat(expiresAt)
                .isBetween(
                        Instant.now().plusSeconds(12 * 3600 - 30), Instant.now().plusSeconds(12 * 3600 + 30));
    }

    @Test
    void aLowercaseCodeFindsTheGameAndTwoGuestsWithTheSameNameAreBothAccepted() throws Exception {
        assertThat(checkIn("opengame", body("Mai", null)).statusCode()).isEqualTo(200);
        assertThat(checkIn("OPENGAME", body("Mai", null)).statusCode()).isEqualTo(200);

        assertThat(attendanceCount()).isEqualTo(2);
    }

    @Test
    void unknownOrMalformedCodeIs404AndNothingIsCalledOrWritten() throws Exception {
        for (String code : new String[] {"NOPE1234", "A".repeat(21), "BAD-CODE"}) {
            HttpResponse<String> response = checkIn(code, body("Mai", null));

            assertThat(response.statusCode()).as(code).isEqualTo(404);
            assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
        }
        assertThat(attendanceCount()).isZero();
        assertThat(PLAYER.bodies).isEmpty();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void aClosedGameIs409StateConflictAndTheRowCountsAreUnchanged() throws Exception {
        insertGame("CLOSED01", "CLOSED", Instant.now().plusSeconds(3600));
        long before = attendanceCount();

        HttpResponse<String> response = checkIn("CLOSED01", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(attendanceCount()).isEqualTo(before);
        assertThat(PLAYER.bodies).isEmpty();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void badNamesAre400AndNothingIsCalledOrWritten() throws Exception {
        for (String name : new String[] {"", "   ", "A".repeat(81), "Ma\\u0007i", "Ma\\u0009i"}) {
            assertThat(checkIn("OPENGAME", body(name, null)).statusCode())
                    .as(name)
                    .isEqualTo(400);
        }
        assertThat(attendanceCount()).isZero();
        assertThat(PLAYER.bodies).isEmpty();
    }

    @Test
    void aNameOfEightyCharactersAndAPaddedNameAreAcceptedTrimmed() throws Exception {
        assertThat(checkIn("OPENGAME", body("A".repeat(80), null)).statusCode()).isEqualTo(200);
        assertThat(checkIn("OPENGAME", body("   Mai   ", null)).statusCode()).isEqualTo(200);

        assertThat(jdbc.sql("SELECT count(*) FROM attendance WHERE display_name = 'Mai'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aLiveAttendanceOfThisGameIsReturnedWithoutWritesOrCredential() throws Exception {
        UUID existing = insertAttendance(gameId, "READY");
        long before = attendanceCount();

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", existing));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode answer = JSON.readTree(response.body());
        assertThat(answer.get("attendanceState").asString()).isEqualTo("READY");
        assertThat(answer.get("created").asBoolean()).isFalse();
        assertThat(answer.get("credential").isNull()).isTrue();
        assertThat(attendanceCount()).isEqualTo(before);
        assertThat(PLAYER.bodies).isEmpty();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void anAttendanceOfAnotherGameOrOneThatLeftOrAnUnknownOneIsIgnoredAndACheckInHappens() throws Exception {
        UUID otherGame = insertGame("OTHER001", "OPEN", Instant.now().plusSeconds(3600));
        UUID[] ignored = {insertAttendance(otherGame, "READY"), insertAttendance(gameId, "LEFT"), UUID.randomUUID()};
        long created = 0;

        for (UUID id : ignored) {
            HttpResponse<String> response = checkIn("OPENGAME", body("Mai", id));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(response.body()).get("created").asBoolean())
                    .isTrue();
            created++;
        }

        assertThat(PLAYER.bodies).hasSize((int) created);
        assertThat(attendanceCount()).isEqualTo(2 + created);
    }

    @Test
    void whenPlayerServiceIsDownTheAnswerIs503AndNothingIsWritten() throws Exception {
        PLAYER.reset(500);

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(response.body()).doesNotContain(TOKEN);
        assertThat(attendanceCount()).isZero();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void whenPlayerServiceRefusesTheAnswersTheAnswerIs400AndNothingIsWritten() throws Exception {
        PLAYER.reset(400);

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(attendanceCount()).isZero();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void whenPlayerServiceIsUnreachableTheAnswerIs503AndNothingIsWritten() throws Exception {
        PLAYER.down();

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(attendanceCount()).isZero();
        assertThat(AUTH.bodies).isEmpty();
    }

    @Test
    void whenAuthServiceIsUnreachableTheAttendanceIsWithdrawnAndTheAnswerIs503WithoutACredential() throws Exception {
        AUTH.down();

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain(TOKEN, "credential");
        assertThat(jdbc.sql("SELECT state FROM attendance").query(String.class).list())
                .containsExactly("LEFT");
    }

    @Test
    void neitherTheNameNorTheAnswersNorTheTokenAppearInTheLogAfterASuccessOrAFailure(CapturedOutput output)
            throws Exception {
        checkIn("OPENGAME", body("Zorbulon", null));
        AUTH.reset(500);
        checkIn("OPENGAME", body("Quuxmire", null));
        PLAYER.reset(500);
        checkIn("OPENGAME", body("Frobnitz", null));

        assertThat(output.getAll()).doesNotContain("Zorbulon", "Quuxmire", "Frobnitz", TOKEN, "positioning");
    }

    @Test
    void whenAuthServiceFailsTheAttendanceIsWithdrawnAndTheAnswerIs503WithoutACredential() throws Exception {
        AUTH.reset(500);

        HttpResponse<String> response = checkIn("OPENGAME", body("Mai", null));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain(TOKEN, "credential");
        assertThat(jdbc.sql("SELECT state FROM attendance").query(String.class).list())
                .containsExactly("LEFT");
    }
}
