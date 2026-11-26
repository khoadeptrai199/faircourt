package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.RouteAccess;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * API foundation AC-04: the gateway's route rules for an anonymous caller (blueprint sections 3 and 15). Only health,
 * the CSRF bootstrap and listed routes are reachable; everything else is 401 JSON, and {@code /internal/**} never
 * reaches a service.
 */
class GatewayAccessMatrixIT extends GatewayTestBase {

    private static void assertUnauthenticated(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"", "\"requestId\":\"");
    }

    @Test
    void healthAndCsrfBootstrapArePublic() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/auth/csrf").statusCode()).isEqualTo(200);
    }

    @Test
    void anyRouteNotInTheTableIs401ForAnonymous() throws Exception {
        assertUnauthenticated(get("/api/v1/anything"));
        assertUnauthenticated(get("/api/v1/public/games/ANYCODE/guests"));
        assertUnauthenticated(get("/"));
    }

    @Test
    void internalRoutesAreNeverForwarded() throws Exception {
        assertUnauthenticated(get("/internal/v1/anything"));
        // Path traversal is rejected (400 by the firewall) or denied (401); either way it is never forwarded.
        assertThat(get("/api/v1/test/../../internal/v1/anything").statusCode()).isIn(400, 401);

        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void theInternalMemberJoinAndMyAttendanceRoutesSentToTheGatewayAre401AndNeverForwarded() throws Exception {
        String game = "66666666-6666-6666-6666-666666666666";
        String user = "11111111-1111-1111-1111-111111111111";

        assertUnauthenticated(get("/internal/v1/games/" + game + "/my-attendance?userId=" + user));
        assertUnauthenticated(send("POST", "/internal/v1/games/" + game + "/member-attendances", bootstrap()));

        assertThat(STUB.received()).noneMatch(r -> r.path().startsWith("/internal/v1/games/"));
    }

    @Test
    void passwordResetRoutesAreForwardedForAnyoneAsPostWithACsrfToken() throws Exception {
        for (String path : new String[] {"/api/v1/auth/password/forgot", "/api/v1/auth/password/reset"}) {
            STUB.clear();

            HttpResponse<String> noToken = send("POST", path, Map.of());
            HttpResponse<String> withToken = send("POST", path, bootstrap());

            assertThat(noToken.statusCode()).as(path + " without token").isEqualTo(403);
            assertThat(withToken.statusCode()).as(path + " with token").isEqualTo(200);
            assertThat(STUB.received())
                    .extracting(StubService.Received::method, StubService.Received::path)
                    .containsExactly(tuple("POST", path));
            // Only POST: everything else is refused before it reaches auth-service.
            STUB.clear();
            assertThat(get(path).statusCode()).as("GET " + path).isEqualTo(401);
            assertThat(send("DELETE", path, bootstrap()).statusCode())
                    .as("DELETE " + path)
                    .isEqualTo(401);
            assertThat(STUB.received()).isEmpty();
        }
    }

    @Test
    void anOversizedPasswordResetBodyIsRefusedBeforeItIsForwarded() throws Exception {
        for (String path : new String[] {"/api/v1/auth/password/forgot", "/api/v1/auth/password/reset"}) {
            STUB.clear();
            java.net.http.HttpRequest.Builder request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"" + "a".repeat(10_000) + "\"}"));
            bootstrap().forEach(request::header);

            HttpResponse<String> response = browser.send(request.build(), HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).as(path).isEqualTo(400);
            assertThat(STUB.received()).as(path).isEmpty();
        }
    }

    @Test
    void guestCheckInIsOpenForAnyoneAsPostWithACsrfTokenAndNeverForwardedWithoutOne() throws Exception {
        String path = "/api/v1/public/games/FRI7Q2K9/guests";

        HttpResponse<String> noToken = send("POST", path, Map.of());
        // An empty JSON object is invalid: 400 from the route itself proves the rule let the caller in.
        HttpResponse<String> withToken = send("POST", path, bootstrap());

        assertThat(noToken.statusCode()).as("without token").isEqualTo(403);
        assertThat(withToken.statusCode()).as("with token").isEqualTo(400);
        assertThat(withToken.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        // Only POST: everything else is refused before it reaches any service.
        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path, bootstrap()).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void memberJoinNeedsACsrfTokenAndASignedInAccountAndIsNeverForwardedOtherwise() throws Exception {
        String path = "/api/v1/games/" + StubService.OPEN_GAME_ID + "/join";

        HttpResponse<String> noToken = send("POST", path, Map.of());
        HttpResponse<String> anonymous = send("POST", path, bootstrap());

        assertThat(noToken.statusCode()).as("without token").isEqualTo(403);
        assertUnauthenticated(anonymous);
        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path, bootstrap()).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void myAttendanceIsReachableByAnonymousOnlyToAnswer401AndNothingIsForwarded() throws Exception {
        String path = "/api/v1/games/" + StubService.OPEN_GAME_ID + "/me";

        // The PLAYER rule lets the controller decide; without any credential it answers 401 itself.
        assertUnauthenticated(get(path));
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path, bootstrap()).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void hostRoutesAreReachableOnlyBySignedInAccountsAndNothingIsForwardedToAnonymous() throws Exception {
        String board = "/api/v1/host/games/" + StubService.OPEN_GAME_ID + "/board";

        assertUnauthenticated(get(board));
        assertUnauthenticated(get("/api/v1/host/games"));
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, board, bootstrap()).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void accountRoutesNeedASignedInAccount() throws Exception {
        assertUnauthenticated(get("/api/v1/me"));
        // Login is public (CSRF token required); logout is for a signed-in account only.
        assertThat(send("POST", "/api/v1/auth/logout", bootstrap()).statusCode())
                .isEqualTo(401);
    }

    @Test
    void aListedPublicRouteIsForwardedToItsService() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/echo");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        assertThat(STUB.received()).extracting(StubService.Received::path).containsExactly("/api/v1/test/echo");
    }

    @Test
    void publicGamePageIsForwardedToPlaymatchForAnonymousGet() throws Exception {
        HttpResponse<String> response = get("/api/v1/public/games/FRI7Q2K9");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(STUB.received())
                .extracting(StubService.Received::method, StubService.Received::path)
                .containsExactly(tuple("GET", "/api/v1/public/games/FRI7Q2K9"));
        // Reading the public page never starts a gateway session.
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void writesOnThePublicGamePathAreNeverForwarded() throws Exception {
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            HttpResponse<String> response = send(method, "/api/v1/public/games/FRI7Q2K9", Map.of());

            // No CSRF token → refused before the route rule.
            assertThat(response.statusCode()).as(method).isIn(401, 403);
        }
        // With a valid CSRF token, the GET-only route rule itself refuses the write.
        Map<String, String> csrfHeader = bootstrap();
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, "/api/v1/public/games/FRI7Q2K9", csrfHeader).statusCode())
                    .as(method + " with CSRF token")
                    .isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void theRouteTableRejectsRoutesOutsideApiV1() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GatewayRoute(
                        "bad", Set.of(HttpMethod.GET), "/internal/v1/**", RouteAccess.PUBLIC, "http://x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
