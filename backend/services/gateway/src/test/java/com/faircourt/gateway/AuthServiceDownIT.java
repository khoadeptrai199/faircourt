package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Account login when auth-service is not running: a clear 503, no session, no throttle count (AD-15). */
class AuthServiceDownIT extends GatewayTestBase {

    @DynamicPropertySource
    static void authServiceIsDown(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.auth-url", StubService::closedPortUrl);
    }

    @Test
    void loginIs503WhenAuthServiceIsDown() throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"a@b.c\",\"password\":\"x\"}"));
        bootstrap().forEach(request::header);

        HttpResponse<String> response = browser.send(request.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }
}
