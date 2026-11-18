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
