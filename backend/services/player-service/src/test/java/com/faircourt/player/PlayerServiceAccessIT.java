package com.faircourt.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real player-service security chain (API foundation, AD-14): health public; every route that no feature opened,
 * including {@code /internal/**}, is 401 JSON; no session cookie is ever created. Access-matrix rows per route are
 * added here when the service gets its first route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlayerServiceAccessIT {

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
    void onlyTheGuestProfileRouteIsOpenUnderInternalAndOnlyForPost() throws Exception {
        // Opened by Guest check-in: POST only. An empty body is a 4xx from the route, never 401 from the security
        // chain.
        assertThat(send("POST", "/internal/v1/players/guests").statusCode()).isNotEqualTo(401);
        assertThat(send("GET", "/internal/v1/players/guests").statusCode()).isEqualTo(401);
        assertThat(send("PUT", "/internal/v1/players/guests").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/internal/v1/players/guests/extra").statusCode())
                .isEqualTo(401);
        assertThat(send("POST", "/api/v1/players/guests").statusCode()).isEqualTo(401);
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
}
