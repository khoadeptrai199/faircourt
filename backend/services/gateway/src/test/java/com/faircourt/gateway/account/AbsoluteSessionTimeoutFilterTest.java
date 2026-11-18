package com.faircourt.gateway.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class AbsoluteSessionTimeoutFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private final AbsoluteSessionTimeoutFilter filter =
            new AbsoluteSessionTimeoutFilter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(8));

    private MockHttpSession run(Instant loginAt) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AbsoluteSessionTimeoutFilter.LOGIN_AT, loginAt);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return session;
    }

    @Test
    void aLoginOlderThanTheAbsoluteTimeoutIsEnded() throws Exception {
        assertThat(run(NOW.minus(Duration.ofHours(8)).minusSeconds(1)).isInvalid())
                .isTrue();
    }

    @Test
    void aRecentLoginIsKept() throws Exception {
        assertThat(run(NOW.minus(Duration.ofHours(7))).isInvalid()).isFalse();
    }

    @Test
    void aSessionWithoutALoginTimeIsLeftAlone() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.isInvalid()).isFalse();
    }
}
