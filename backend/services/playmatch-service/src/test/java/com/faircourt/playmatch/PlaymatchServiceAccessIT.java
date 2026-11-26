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
            String token = new IdentityToken(System.getenv("INTERNAL_TOKEN_SECRET"), Clock.systemUTC())
                    .issue(UUID.randomUUID());

            HttpResponse<String> board = sendWithToken("/api/v1/host/games/" + gameId + "/board", token);
            HttpResponse<String> list = sendWithToken("/api/v1/host/games", token);

            assertThat(board.statusCode()).isEqualTo(403);
            assertThat(board.body()).contains("\"code\":\"FORBIDDEN\"");
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(list.body()).isEqualTo("{\"games\":[]}");
        } finally {
            jdbc.sql("DELETE FROM game WHERE id = :id").param("id", gameId).update();
        }
    }

    private HttpResponse<String> sendWithToken(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header(IdentityTokenHeader.NAME, token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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
