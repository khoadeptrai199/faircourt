package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * API foundation AC-09 (AD-15): a service that is down or too slow → 503 {@code SERVICE_UNAVAILABLE}; an error the
 * service does send is passed through unchanged. The read timeout is 1 s in these tests (5 s in the app).
 */
class DownstreamErrorIT extends GatewayTestBase {

    @Test
    void serviceDownIs503ServiceUnavailable() throws Exception {
        HttpResponse<String> response = get("/api/v1/down/anything");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body())
                .startsWith("{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"")
                .contains("\"requestId\":\"");
    }

    @Test
    void serviceTooSlowIs503ServiceUnavailable() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/slow");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    void serviceErrorStatusAndBodyArePassedThroughUnchanged() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/conflict");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).isEqualTo(StubService.CONFLICT_BODY);
    }
}
