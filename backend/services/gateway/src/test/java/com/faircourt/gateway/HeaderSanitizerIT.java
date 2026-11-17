package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.gateway.routing.HeaderSanitizerAccess;
import com.sun.net.httpserver.Headers;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * AD-14 "strips client identity headers": credentials and identity-like headers a browser sends never reach a service.
 */
class HeaderSanitizerIT extends GatewayTestBase {

    @Test
    void credentialsAndIdentityLikeHeadersAreRemovedBeforeForwarding() throws Exception {
        HttpResponse<String> response = send(
                "GET",
                "/api/v1/test/echo",
                Map.of(
                        "Cookie", "JSESSIONID=stolen",
                        "Authorization", "Bearer forged",
                        "Forwarded", "for=6.6.6.6",
                        "X-Forwarded-For", "6.6.6.6",
                        "X-Internal-Caller", "host-user-id",
                        "X-Custom-Ok", "kept"));

        assertThat(response.statusCode()).isEqualTo(200);
        Headers forwarded = STUB.received().getFirst().headers();
        assertThat(forwarded.containsKey("Cookie")).isFalse();
        assertThat(forwarded.containsKey("Authorization")).isFalse();
        assertThat(forwarded.containsKey("X-internal-caller")).isFalse();
        assertThat(String.valueOf(forwarded.get("Forwarded"))).doesNotContain("6.6.6.6");
        assertThat(String.valueOf(forwarded.get("X-forwarded-for"))).doesNotContain("6.6.6.6");
        assertThat(forwarded.getFirst("X-custom-ok")).isEqualTo("kept");
    }

    @Test
    void headerRulesMatchCaseInsensitively() {
        for (String name : new String[] {
            "COOKIE", "authorization", "Proxy-Authorization", "x-forwarded-host", "X-INTERNAL-IDENTITY"
        }) {
            assertThat(HeaderSanitizerAccess.mustRemove(name)).as(name).isTrue();
        }
        assertThat(HeaderSanitizerAccess.mustRemove("Content-Type")).isFalse();
        assertThat(HeaderSanitizerAccess.mustRemove("X-Request-Id")).isFalse();
    }
}
