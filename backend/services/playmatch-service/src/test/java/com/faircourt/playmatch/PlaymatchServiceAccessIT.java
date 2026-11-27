package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The real playmatch-service security chain (API foundation, AD-14): health public; every route that no feature opened,
 * including {@code /internal/**}, is 401 JSON; no HTTP session cookie is ever created. Plus one access-matrix row
 * per opened route (public game page: anyone, GET only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaymatchServiceAccessIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcClient jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsPublic() throws Exception {
        assertThat(send("GET", "/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void routesNoFeatureOpenedAre401JsonWithoutASession() throws Exception {
        for (String path : new String[] {"/api/v1/anything", "/internal/v1/anything"}) {
            for (String method : new String[] {"GET", "POST"}) {
                HttpResponse<String> response = send(method, path);

                assertThat(response.statusCode()).as(method + " " + path).isEqualTo(401);
                assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
                assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
            }
        }
    }

    @Test
    void guestCheckInRouteIsInternalAndOpenForPostOnly() throws Exception {
        String path = "/internal/v1/games/by-code/ABC12345/guest-check-ins";
        // Opened by Guest check-in. An empty body is a 4xx from the route, never 401 from the security chain.
        assertThat(send("POST", path).statusCode()).isNotEqualTo(401);
        assertThat(send("GET", path).statusCode()).isEqualTo(401);
        assertThat(send("PUT", path).statusCode()).isEqualTo(401);
        assertThat(send("POST", path + "/extra").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/games/by-code/ABC12345/guest-check-ins")
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    void memberJoinRouteIsInternalAndOpenForPostOnly() throws Exception {
        String path = "/internal/v1/games/22222222-2222-2222-2222-222222222222/member-attendances";
        // Opened by My attendance. An empty body is a 4xx from the route, never 401 from the security chain.
        assertThat(send("POST", path).statusCode()).isNotEqualTo(401);
        assertThat(send("GET", path).statusCode()).isEqualTo(401);
        assertThat(send("PUT", path).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", path).statusCode()).isEqualTo(401);
        assertThat(send("POST", path + "/extra").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/games/22222222-2222-2222-2222-222222222222/member-attendances")
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    void myAttendanceRouteIsInternalAndOpenForGetOnly() throws Exception {
        String path = "/internal/v1/games/22222222-2222-2222-2222-222222222222/my-attendance";
        // Opened by My attendance. Without a parameter the route's own 400, never 401 from the security chain.
        assertThat(send("GET", path).statusCode()).isEqualTo(400);
        assertThat(send("POST", path).statusCode()).isEqualTo(401);
        assertThat(send("PUT", path).statusCode()).isEqualTo(401);
        assertThat(send("DELETE", path).statusCode()).isEqualTo(401);
        assertThat(send("GET", path + "/extra").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/v1/games/22222222-2222-2222-2222-222222222222/my-attendance")
                        .statusCode())
                .isEqualTo(401);
    }

    @Test
    void hostRoutesAreOpenForGetOnlyAndTheServiceAnswersWithoutTokenAs401() throws Exception {
        String board = "/api/v1/host/games/22222222-2222-2222-2222-222222222222/board";
        String list = "/api/v1/host/games";

        // Opened by Host board. Without an identity token the route's own 401, with the JSON body.
        for (String path : new String[] {board, list}) {
            HttpResponse<String> get = send("GET", path);
            assertThat(get.statusCode()).as(path).isEqualTo(401);
            assertThat(get.body()).contains("\"code\":\"UNAUTHENTICATED\"");
            for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
                assertThat(send(method, path).statusCode())
                        .as(method + " " + path)
                        .isEqualTo(401);
            }
        }
        assertThat(send("GET", board + "/extra").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/internal/v1/host/games").statusCode()).isEqualTo(401);
    }

    @Test
    void aSignedInAccountThatIsNotTheHostGets403OnTheBoardAndItsOwnEmptyList() throws Exception {
        UUID gameId = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count)
				VALUES (:id, :code, :host, 'Access row', 'Court House D7', :startsAt, :endsAt, 2)""")
                .param("id", gameId)
                .param("code", "ACCROW01")
                .param("host", UUID.randomUUID())
                .param("startsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .param("endsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).plusHours(2))
                .update();
        try {
            UUID other = UUID.randomUUID();
            String boardPath = "/api/v1/host/games/" + gameId + "/board";

            HttpResponse<String> board = sendWithToken("GET", boardPath, token(other, "GET", boardPath));
            HttpResponse<String> list =
                    sendWithToken("GET", "/api/v1/host/games", token(other, "GET", "/api/v1/host/games"));
            assertThat(board.statusCode()).isEqualTo(403);
            assertThat(board.body()).contains("\"code\":\"FORBIDDEN\"");
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(list.body()).isEqualTo("{\"games\":[]}");
        } finally {
            jdbc.sql("DELETE FROM game WHERE id = :id").param("id", gameId).update();
        }
    }

    /** Match suggestions, TC-24: no identity token is 401 JSON; the route is open for POST only. */
    @Test
    void suggestionsRouteIsOpenForPostOnlyAndAnswersWithoutTokenAs401() throws Exception {
        String path = "/api/v1/host/games/22222222-2222-2222-2222-222222222222/suggestions";

        HttpResponse<String> post = send("POST", path);

        assertThat(post.statusCode()).isEqualTo(401);
        assertThat(post.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(send("POST", path + "/extra").statusCode()).isEqualTo(401);
    }

    /** Match confirm, TC-21: no identity token is 401 JSON; the route is open for POST only. */
    @Test
    void matchesRouteIsOpenForPostOnlyAndAnswersWithoutTokenAs401() throws Exception {
        String validBody = "{\"courtNo\":1,\"teams\":{\"A\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID()
                + "\"]," + "\"B\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]}}";
        String path = "/api/v1/host/games/22222222-2222-2222-2222-222222222222/matches";

        HttpResponse<String> noBody = sendWithToken("POST", path, null, "");

        assertThat(noBody.statusCode()).isEqualTo(401);
        assertThat(noBody.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        HttpResponse<String> post = sendWithToken("POST", path, null, validBody);

        assertThat(post.statusCode()).isEqualTo(401);
        assertThat(post.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(send("POST", path + "/extra").statusCode()).isEqualTo(401);
    }

    private static String token(UUID user, String method, String path) {
        return new IdentityToken(System.getenv("INTERNAL_TOKEN_SECRET"), Clock.systemUTC())
                .issue(user, "playmatch-service", method, path);
    }

    private HttpResponse<String> sendWithToken(String method, String path, String token) throws Exception {
        return sendWithToken(method, path, token, "{}");
    }

    private HttpResponse<String> sendWithToken(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(
                        method,
                        method.equals("GET")
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        if (!method.equals("GET")) {
            request.header("Content-Type", "application/json");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void hostAttendanceRouteIsOpenForPatchOnlyAndAnswersAccountHostAndMissingRowsAsDesigned() throws Exception {
        UUID gameId = UUID.randomUUID();
        UUID host = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count)
				VALUES (:id, :code, :host, 'Access row', 'Court House D7', :startsAt, :endsAt, 2)""")
                .param("id", gameId)
                .param("code", "ACCROW02")
                .param("host", host)
                .param("startsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .param("endsAt", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).plusHours(2))
                .update();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state)
				VALUES (:id, :game, :player, 'Access guest', 'PENDING')""")
                .param("id", guest)
                .param("game", gameId)
                .param("player", UUID.randomUUID())
                .update();
        try {
            String path = "/api/v1/host/games/" + gameId + "/attendance/" + guest;
            String body = "{\"state\":\"READY\"}";

            // No token: the route's own 401.
            assertThat(sendWithToken("PATCH", path, null, body).statusCode()).isEqualTo(401);
            // Other methods and a longer path are not opened.
            for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
                assertThat(sendWithToken(method, path, token(host, method, path), body)
                                .statusCode())
                        .as(method)
                        .isEqualTo(401);
            }
            assertThat(sendWithToken("PATCH", path + "/extra", null, body).statusCode())
                    .isEqualTo(401);
            // An account that is not this game's host.
            HttpResponse<String> other = sendWithToken("PATCH", path, token(UUID.randomUUID(), "PATCH", path), body);
            assertThat(other.statusCode()).isEqualTo(403);
            assertThat(other.body()).contains("\"code\":\"FORBIDDEN\"");
            assertThat(stateOf(guest)).isEqualTo("PENDING");
            // Unknown game, malformed game id, malformed attendance id.
            String unknownGame = "/api/v1/host/games/" + UUID.randomUUID() + "/attendance/" + guest;
            String badGame = "/api/v1/host/games/not-a-uuid/attendance/" + guest;
            String badAttendance = "/api/v1/host/games/" + gameId + "/attendance/not-a-uuid";
            for (String p : new String[] {unknownGame, badGame, badAttendance}) {
                assertThat(sendWithToken("PATCH", p, token(host, "PATCH", p), body)
                                .statusCode())
                        .as(p)
                        .isEqualTo(404);
            }
            assertThat(stateOf(guest)).isEqualTo("PENDING");
            // The host.
            HttpResponse<String> ok = sendWithToken("PATCH", path, token(host, "PATCH", path), body);
            assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
            assertThat(stateOf(guest)).isEqualTo("READY");
        } finally {
            jdbc.sql("DELETE FROM attendance WHERE game_id = :id")
                    .param("id", gameId)
                    .update();
            jdbc.sql("DELETE FROM game WHERE id = :id").param("id", gameId).update();
        }
    }

    @Test
    void createGameRouteIsOpenForPostOnlyAndNeedsAnAccountToken() throws Exception {
        String path = "/api/v1/games";
        UUID account = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        String body = "{\"title\":\"Access row\",\"venue\":\"Court House D7\",\"startsAt\":\"" + start
                + "\",\"endsAt\":\"" + start.plusHours(2)
                + "\",\"courtCount\":2,\"matchFormat\":\"DOUBLES\",\"minSets\":6}";
        try {
            // No token, and an invalid or foreign token (not a valid account token for this route) -> 401. The real
            // guest-cookie row is in GatewayAccessMatrixIT; the service knows only account tokens.
            assertThat(sendWithToken("POST", path, null, body).statusCode()).isEqualTo(401);
            assertThat(sendWithToken("POST", path, "invalid-or-foreign-token", body)
                            .statusCode())
                    .isEqualTo(401);
            assertThat(hostedBy(account)).isZero();
            // Other methods are not opened.
            for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
                assertThat(sendWithToken(method, path, token(account, method, path), body)
                                .statusCode())
                        .as(method)
                        .isEqualTo(401);
            }
            // A signed-in account.
            HttpResponse<String> created = sendWithToken("POST", path, token(account, "POST", path), body);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            assertThat(hostedBy(account)).isEqualTo(1);
        } finally {
            jdbc.sql("DELETE FROM game WHERE host_user_id = :host")
                    .param("host", account)
                    .update();
        }
    }

    private int hostedBy(UUID account) {
        return jdbc.sql("SELECT count(*) FROM game WHERE host_user_id = :host")
                .param("host", account)
                .query(Integer.class)
                .single();
    }

    private String stateOf(UUID attendanceId) {
        return jdbc.sql("SELECT state FROM attendance WHERE id = :id")
                .param("id", attendanceId)
                .query(String.class)
                .single();
    }

    @Test
    void publicGamePageIsOpenToAnonymousForGetOnly() throws Exception {
        // Unknown code: the route is open, so the answer is the controller's 404, not the security chain's 401.
        HttpResponse<String> get = send("GET", "/api/v1/public/games/UNKNOWN1");
        assertThat(get.statusCode()).isEqualTo(404);
        assertThat(get.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(get.headers().allValues("Set-Cookie")).isEmpty();

        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, "/api/v1/public/games/UNKNOWN1").statusCode())
                    .as(method)
                    .isEqualTo(401);
        }
        // Only one path segment is open.
        assertThat(send("GET", "/api/v1/public/games/UNKNOWN1/guests").statusCode())
                .isEqualTo(401);
    }
}
