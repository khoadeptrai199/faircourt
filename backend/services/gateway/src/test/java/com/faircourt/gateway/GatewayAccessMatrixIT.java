package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.RouteAccess;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * API foundation AC-04: the gateway's route rules for an anonymous caller (blueprint sections 3 and 15). Only health,
 * the CSRF bootstrap and listed routes are reachable; everything else is 401 JSON, and {@code /internal/**} never
 * reaches a service.
 */
class GatewayAccessMatrixIT extends GuestTestBase {

    private static final String CREATE_GAME = "/api/v1/games";
    private static final String PLAYMATCH = "playmatch-service";
    private final IdentityToken tokens = new IdentityToken(System.getenv("INTERNAL_TOKEN_SECRET"), Clock.systemUTC());

    private void signIn() throws Exception {
        HttpResponse<String> login = postJson(
                "/api/v1/auth/login", loginJson("host@faircourt.local", StubService.ACCOUNT_PASSWORD), bootstrap());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
    }

    private List<StubService.Received> createGameCalls() {
        return STUB.received().stream()
                .filter(r -> r.path().equals(CREATE_GAME))
                .toList();
    }

    /** Create game, TC-11: anonymous and guest-only are 401, no CSRF is 403, an account with CSRF is forwarded once. */
    @Test
    void createGameNeedsAnAccountAndACsrfTokenAndIsForwardedOnceWithATokenBoundToIt() throws Exception {
        assertUnauthenticated(send("POST", CREATE_GAME, bootstrap()));
        HttpResponse<String> checkIn = checkIn(OPEN_CODE, guestJson("Mai"));
        STUB.knownGuestToken(tokenOf(guestCookieOf(checkIn)), UUID.randomUUID().toString());
        assertUnauthenticated(send("POST", CREATE_GAME, bootstrap()));
        assertThat(createGameCalls()).isEmpty();

        signIn();
        HttpResponse<String> noCsrf = send("POST", CREATE_GAME, Map.of());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(noCsrf.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(createGameCalls()).isEmpty();

        HttpResponse<String> response = send("POST", CREATE_GAME, bootstrap());

        // The stub answers every unknown path with 200 {"ok":true}; the gateway passes the answer through.
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(createGameCalls()).hasSize(1);
        List<String> values = createGameCalls().getFirst().headers().get(IdentityTokenHeader.NAME);
        assertThat(values).hasSize(1);
        assertThat(tokens.verify(values.getFirst(), PLAYMATCH, "POST", CREATE_GAME))
                .contains(new CallerIdentity.Account(UUID.fromString(StubService.ACCOUNT_USER_ID)));
        assertThat(tokens.verify(values.getFirst(), PLAYMATCH, "POST", "/api/v1/games/x/join"))
                .isEmpty();
        // Only POST is open on this path: a signed-in account sending another method is refused 403 and nothing is
        // forwarded.
        for (String method : new String[] {"PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, CREATE_GAME, bootstrap()).statusCode())
                    .as(method)
                    .isEqualTo(403);
            assertThat(createGameCalls()).as(method + " forwarded").hasSize(1);
        }
    }

    /** Create game, TC-12: join and "my game" keep their own rows (answered by the gateway, nothing forwarded). */
    @Test
    void theCreateGameRowDoesNotTakeOverJoinAndMyGame() throws Exception {
        signIn();
        String join = "/api/v1/games/" + StubService.OPEN_GAME_ID + "/join";
        String me = "/api/v1/games/" + StubService.OPEN_GAME_ID + "/me";

        HttpResponse<String> joined = send("POST", join, bootstrap());
        HttpResponse<String> mine = get(me);

        assertThat(joined.statusCode()).as(joined.body()).isIn(200, 201);
        assertThat(mine.statusCode()).as(mine.body()).isEqualTo(200);
        assertThat(STUB.received()).noneMatch(r -> r.path().startsWith("/api/v1/games"));
        assertThat(STUB.received()).anyMatch(r -> r.path().endsWith("/member-attendances"));
        assertThat(STUB.received()).anyMatch(r -> r.path().endsWith("/my-attendance"));
    }

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
        String attendance =
                "/api/v1/host/games/" + StubService.OPEN_GAME_ID + "/attendance/" + StubService.OPEN_GAME_ID;
        assertUnauthenticated(send("PATCH", attendance, bootstrap()));
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, board, bootstrap()).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    /** Match suggestions, TC-23: anonymous and guest-only 401, no CSRF 403, account with CSRF forwarded once. */
    @Test
    void suggestionsNeedAnAccountAndACsrfTokenAndAreForwardedOnceWithATokenBoundToThePath() throws Exception {
        String path = "/api/v1/host/games/" + StubService.OPEN_GAME_ID + "/suggestions";
        java.util.function.Supplier<List<StubService.Received>> calls = () ->
                STUB.received().stream().filter(r -> r.path().equals(path)).toList();

        assertUnauthenticated(send("POST", path, bootstrap()));
        HttpResponse<String> checkIn = checkIn(OPEN_CODE, guestJson("Mai"));
        STUB.knownGuestToken(tokenOf(guestCookieOf(checkIn)), UUID.randomUUID().toString());
        assertUnauthenticated(send("POST", path, bootstrap()));
        assertThat(calls.get()).isEmpty();

        signIn();
        HttpResponse<String> noCsrf = send("POST", path, Map.of());
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(noCsrf.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(calls.get()).isEmpty();

        HttpResponse<String> response = send("POST", path, bootstrap());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls.get()).hasSize(1);
        List<String> values = calls.get().getFirst().headers().get(IdentityTokenHeader.NAME);
        assertThat(values).hasSize(1);
        assertThat(tokens.verify(values.getFirst(), PLAYMATCH, "POST", path))
                .contains(new CallerIdentity.Account(UUID.fromString(StubService.ACCOUNT_USER_ID)));
        assertThat(tokens.verify(values.getFirst(), PLAYMATCH, "GET", path)).isEmpty();
        // Only POST is open on this path: GET is refused and nothing more is forwarded.
        // Observed: a signed-in GET on the POST-only path is refused with 403, not routed.
        HttpResponse<String> get = get(path);
        assertThat(get.statusCode()).isEqualTo(403);
        assertThat(calls.get()).hasSize(1);
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
