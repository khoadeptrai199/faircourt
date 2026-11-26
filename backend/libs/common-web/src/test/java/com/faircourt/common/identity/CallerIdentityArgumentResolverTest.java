package com.faircourt.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class CallerIdentityArgumentResolverTest {

    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SERVICE = "playmatch-service";
    private final IdentityToken tokens = new IdentityToken(
            "resolver-test-secret-resolver-test-secret",
            Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC));
    private final CallerIdentityArgumentResolver resolver = new CallerIdentityArgumentResolver(tokens, SERVICE);

    private Object resolve(String... headerValues) {
        return resolveAs("GET", "/api/v1/host/games", headerValues);
    }

    private Object resolveAs(String method, String path, String... headerValues) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        for (String value : headerValues) {
            request.addHeader(IdentityTokenHeader.NAME, value);
        }
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private static void assertUnauthenticated(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void noHeaderIsAnonymous() {
        assertThat(resolve()).isEqualTo(new CallerIdentity.Anonymous());
    }

    @Test
    void aValidTokenIsTheAccount() {
        assertThat(resolve(tokens.issue(USER, SERVICE, "GET", "/api/v1/host/games")))
                .isEqualTo(new CallerIdentity.Account(USER));
    }

    @Test
    void anInvalidTokenOrTwoHeadersIs401() {
        String valid = tokens.issue(USER, SERVICE, "GET", "/api/v1/host/games");

        assertUnauthenticated(() -> resolve("garbage"));
        assertUnauthenticated(() -> resolve(valid, valid));
        assertThatThrownBy(() -> resolve("")).isInstanceOf(ApiException.class);
    }

    @Test
    void aTokenIssuedForAnotherMethodPathOrServiceIs401() {
        String forPatch = tokens.issue(USER, SERVICE, "PATCH", "/a/b");

        assertThat(resolveAs("PATCH", "/a/b", forPatch)).isEqualTo(new CallerIdentity.Account(USER));
        assertUnauthenticated(() -> resolveAs("PATCH", "/a/b", tokens.issue(USER, SERVICE, "GET", "/a/b")));
        assertUnauthenticated(() -> resolveAs("PATCH", "/a/c", forPatch));
        assertUnauthenticated(() -> resolveAs("PATCH", "/a/b", tokens.issue(USER, "player-service", "PATCH", "/a/b")));
    }
}
