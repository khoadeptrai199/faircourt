package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.RouteAccess;
import java.net.http.HttpResponse;
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
        assertUnauthenticated(get("/api/v1/public/sessions/ANYCODE"));
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
    void theRouteTableRejectsRoutesOutsideApiV1() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GatewayRoute(
                        "bad", Set.of(HttpMethod.GET), "/internal/v1/**", RouteAccess.PUBLIC, "http://x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
