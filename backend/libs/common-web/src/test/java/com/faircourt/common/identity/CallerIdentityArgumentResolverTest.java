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
    private final IdentityToken tokens = new IdentityToken(
            "resolver-test-secret-resolver-test-secret",
            Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC));
    private final CallerIdentityArgumentResolver resolver = new CallerIdentityArgumentResolver(tokens);

    private Object resolve(String... headerValues) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/host/games");
        for (String value : headerValues) {
            request.addHeader(IdentityTokenHeader.NAME, value);
        }
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    void noHeaderIsAnonymous() {
        assertThat(resolve()).isEqualTo(new CallerIdentity.Anonymous());
    }

    @Test
    void aValidTokenIsTheAccount() {
        assertThat(resolve(tokens.issue(USER))).isEqualTo(new CallerIdentity.Account(USER));
    }

    @Test
    void anInvalidTokenOrTwoHeadersIs401() {
        String valid = tokens.issue(USER);

        assertThatThrownBy(() -> resolve("garbage"))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> resolve(valid, valid))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> resolve("")).isInstanceOf(ApiException.class);
    }
}
