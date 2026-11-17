package com.faircourt.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private record Seen(String header, String mdc, java.util.List<String> headerValues) {}

    private Seen run(RequestIdFilter filter, String incoming, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        if (incoming != null) {
            request.addHeader(RequestIdFilter.HEADER, incoming);
        }
        AtomicReference<Seen> seen = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> {
            HttpServletRequest http = (HttpServletRequest) req;
            seen.set(new Seen(
                    http.getHeader(RequestIdFilter.HEADER),
                    MDC.get(RequestIdFilter.MDC_KEY),
                    Collections.list(http.getHeaders(RequestIdFilter.HEADER))));
        });
        return seen.get();
    }

    @Test
    void serviceKeepsTheIdForwardedByTheGateway() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Seen seen = run(new RequestIdFilter(true), "gw-123", response);

        assertThat(seen.header()).isEqualTo("gw-123");
        assertThat(seen.mdc()).isEqualTo("gw-123");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("gw-123");
    }

    @Test
    void serviceReplacesAMalformedIncomingId() throws Exception {
        Seen seen = run(new RequestIdFilter(true), "bad id\nwith newline", new MockHttpServletResponse());

        assertThat(seen.header()).isNotEqualTo("bad id\nwith newline").matches("[0-9a-f-]{36}");
    }

    @Test
    void gatewayReplacesAClientSentIdWithAGeneratedOne() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Seen seen = run(new RequestIdFilter(false), "evil", response);

        assertThat(seen.header()).isNotEqualTo("evil").matches("[0-9a-f-]{36}");
        assertThat(seen.headerValues()).containsExactly(seen.header());
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seen.header());
    }

    @Test
    void mdcIsClearedAfterTheRequest() throws Exception {
        run(new RequestIdFilter(true), "gw-123", new MockHttpServletResponse());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
