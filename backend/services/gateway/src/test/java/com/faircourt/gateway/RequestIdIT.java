package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * API foundation AC-05 (gateway side) and AC-10: the gateway creates the request ID, replaces any client-sent one,
 * forwards it to the service, returns it in the response header and error body, and logs it.
 */
@ExtendWith(OutputCaptureExtension.class)
class RequestIdIT extends GatewayTestBase {

    @Test
    void clientSentRequestIdIsReplacedBeforeForwarding() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/test/echo", Map.of("X-Request-Id", "evil"));

        String forwarded = STUB.received().getFirst().headers().getFirst("X-Request-Id");
        assertThat(forwarded).isNotEqualTo("evil").matches("[0-9a-f-]{36}");
        assertThat(response.headers().firstValue("X-Request-Id")).hasValue(forwarded);
    }

    @Test
    void oneRequestIdLinksTheGatewayLogTheResponseHeaderAndTheErrorBody(CapturedOutput output) throws Exception {
        HttpResponse<String> response = get("/api/v1/down/anything");

        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(response.body()).contains("\"requestId\":\"" + requestId + "\"");
        assertThat(output.getOut()).contains("[" + requestId + "]", "GET /api/v1/down/anything -> 503");
    }
}
