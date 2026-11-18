package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real auth-service security chain (API foundation, AD-14): health public; every route that no feature opened,
 * including {@code /internal/**}, is 401 JSON; no session cookie is ever created. Access-matrix rows per route are
 * added here when the service gets its first route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServiceAccessIT {

    @Value("${local.server.port}")
    int port;

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
    void passwordResetRoutesAreOpenForPostOnlyAndSessionCheckForGetOnly() throws Exception {
        // Opened by Password reset. A request without a body is a 4xx from the route, never 401 from the security
        // chain.
        assertThat(send("POST", "/api/v1/auth/password/forgot").statusCode()).isNotEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/password/reset").statusCode()).isNotEqualTo(401);
        assertThat(send("GET", "/api/v1/auth/password/forgot").statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/api/v1/auth/password/reset").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/password/other").statusCode()).isEqualTo(401);

        String check = "/internal/v1/auth/accounts/" + java.util.UUID.randomUUID() + "/session-check";
        assertThat(send("GET", check + "?since=2026-11-18T10:00:00Z").statusCode())
                .isEqualTo(200);
        assertThat(send("POST", check).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/internal/v1/auth/accounts/x/other").statusCode())
                .isEqualTo(401);
    }

    @Test
    void onlyTheVerifyRouteIsOpenUnderInternalAndOnlyForPost() throws Exception {
        // Opened by Account login: POST only. The route answers 4xx for a bad request, never 401 from the security
        // chain
        // for POST; GET and every other internal path stay denied.
        assertThat(send("POST", "/internal/v1/auth/verify").statusCode()).isNotEqualTo(401);
        assertThat(send("GET", "/internal/v1/auth/verify").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/internal/v1/auth/verify/extra").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/internal/v1/auth/other").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/auth/verify").statusCode()).isEqualTo(401);
    }
}
