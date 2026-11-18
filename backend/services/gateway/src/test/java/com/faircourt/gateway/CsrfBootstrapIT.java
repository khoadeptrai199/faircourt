package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * API foundation AC-02, AC-03 (blueprint section 0): the public CSRF bootstrap and the CSRF check on writes. The
 * header name is read from the response, not assumed.
 */
class CsrfBootstrapIT extends GatewayTestBase {

    @Test
    void bootstrapReturnsHeaderNameAndTokenAndAnHttpOnlyLaxSessionCookie() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/csrf");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).matches("\\{\"headerName\":\"[^\"]+\",\"token\":\"[^\"]+\"}");
        assertThat(response.body()).contains("\"headerName\":\"X-CSRF-TOKEN\"");
        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie -> assertThat(cookie)
                        .startsWith("JSESSIONID=")
                        .containsIgnoringCase("HttpOnly")
                        .containsIgnoringCase("SameSite=Lax"));
    }

    @Test
    void writeWithoutTokenIs403AndNeverReachesTheService() throws Exception {
        bootstrap();

        HttpResponse<String> response = send("POST", "/api/v1/test/echo", Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void writeWithAWrongTokenIs403() throws Exception {
        String headerName = bootstrap().keySet().iterator().next();

        HttpResponse<String> response = send("POST", "/api/v1/test/echo", Map.of(headerName, "wrong"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void writeWithTheBootstrappedTokenReachesTheServiceWithoutBrowserCookies() throws Exception {
        Map<String, String> csrfHeader = bootstrap();

        HttpResponse<String> response = send("POST", "/api/v1/test/echo", csrfHeader);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(STUB.received()).hasSize(1);
        assertThat(STUB.received().getFirst().headers().containsKey("Cookie"))
                .as("the gateway session cookie must not reach a service")
                .isFalse();
    }

    @Test
    void everyWriteMethodNeedsTheToken() throws Exception {
        bootstrap();

        for (String method : new String[] {"PUT", "PATCH", "DELETE"}) {
            HttpResponse<String> response = send(method, "/api/v1/test/echo", Map.of());

            assertThat(response.statusCode()).as(method).isEqualTo(403);
            assertThat(response.body()).as(method).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aTokenFromAnotherBrowserSessionIsRejected() throws Exception {
        Map<String, String> tokenOfBrowserA = bootstrap();
        newBrowser();
        bootstrap();

        HttpResponse<String> response = send("POST", "/api/v1/test/echo", tokenOfBrowserA);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aReadOnlyRouteRefusesWritesEvenWithAValidToken() throws Exception {
        Map<String, String> csrfHeader = bootstrap();

        HttpResponse<String> response = send("DELETE", "/api/v1/readonly/thing", csrfHeader);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(STUB.received()).isEmpty();
        assertThat(get("/api/v1/readonly/thing").statusCode()).isEqualTo(200);
    }
}
