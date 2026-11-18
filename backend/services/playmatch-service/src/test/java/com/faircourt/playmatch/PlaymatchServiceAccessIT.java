package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The real playmatch-service security chain (API foundation, AD-14): health public; every route that no feature opened,
 * including {@code /internal/**}, is 401 JSON; no HTTP session cookie is ever created. Plus one access-matrix row
 * per opened route (public game page: anyone, GET only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaymatchServiceAccessIT {

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
