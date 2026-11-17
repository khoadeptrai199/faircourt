package com.faircourt.common.web.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.web.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * API foundation AC-01, AC-05 (service side), AC-06: every error a service produces has the body
 * {@code {code, message, requestId}} with the matching status, our own generic message, and the request ID the
 * gateway forwarded, which also appears in the service log.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"logging.pattern.level=%5p [%X{requestId:-}]", "faircourt.request-id.trust-incoming=true"})
@ExtendWith(OutputCaptureExtension.class)
class ErrorContractIT {

    private static final String REQUEST_ID = "contract-req-1";

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Request-Id", REQUEST_ID)
                .method(
                        method,
                        jsonBody == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertError(HttpResponse<String> response, ErrorCode code) {
        assertThat(response.statusCode())
                .as(response.body())
                .isEqualTo(code.status().value());
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body())
                .isEqualTo("{\"code\":\"" + code.name() + "\",\"message\":\"" + code.defaultMessage()
                        + "\",\"requestId\":\"" + REQUEST_ID + "\"}");
    }

    @Test
    void apiExceptionKeepsItsCodeAndStatus() throws Exception {
        assertError(send("GET", "/api/v1/test/api-exception", null), ErrorCode.MATCH_CONFLICT);
        assertError(send("GET", "/api/v1/test/unavailable", null), ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void invalidBodyUnreadableJsonAndWrongParameterTypeAre400() throws Exception {
        assertError(send("POST", "/api/v1/test/validated", "{\"name\":\"\"}"), ErrorCode.VALIDATION_FAILED);
        assertError(send("POST", "/api/v1/test/validated", "{not json"), ErrorCode.VALIDATION_FAILED);
        assertError(send("GET", "/api/v1/test/typed?n=abc", null), ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void unknownPathUnderAnOpenPrefixIs404AndWrongMethodIs405() throws Exception {
        assertError(send("GET", "/api/v1/test/does-not-exist", null), ErrorCode.NOT_FOUND);
        assertError(send("POST", "/api/v1/test/get-only", "{}"), ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void errorsRaisedWithAStatusKeepThatStatusAndItsCode() throws Exception {
        assertError(send("GET", "/api/v1/test/status-conflict", null), ErrorCode.STATE_CONFLICT);
        assertError(send("GET", "/api/v1/test/status-not-found", null), ErrorCode.NOT_FOUND);
    }

    @Test
    void securityExceptionsInsideAServiceAre403And401NotServerErrors() throws Exception {
        assertError(send("GET", "/api/v1/test/access-denied", null), ErrorCode.FORBIDDEN);
        assertError(send("GET", "/api/v1/test/not-authenticated", null), ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void routeThatNoFeatureOpenedIs401() throws Exception {
        assertError(send("GET", "/api/v1/closed", null), ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void databaseConstraintIs409AndUnexpectedErrorIs500WithoutLeakingDetails() throws Exception {
        HttpResponse<String> integrity = send("GET", "/api/v1/test/integrity", null);
        HttpResponse<String> boom = send("GET", "/api/v1/test/boom", null);

        assertError(integrity, ErrorCode.STATE_CONFLICT);
        assertError(boom, ErrorCode.INTERNAL_ERROR);
        assertThat(integrity.body() + boom.body()).doesNotContain("secret", "Exception", "constraint", "at com.");
    }

    @Test
    void forwardedRequestIdIsInTheResponseHeaderAndTheServiceLog(CapturedOutput output) throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/test/api-exception", null);

        assertThat(response.headers().firstValue("X-Request-Id")).hasValue(REQUEST_ID);
        assertThat(output.getOut()).contains("[" + REQUEST_ID + "]", "GET /api/v1/test/api-exception -> 409");
    }
}
