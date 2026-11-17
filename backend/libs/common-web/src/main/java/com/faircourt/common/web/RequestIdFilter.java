package com.faircourt.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request ID for logs and error bodies (AD-04, AD-14).
 *
 * <ul>
 *   <li>Services ({@code trustIncoming = true}): use the {@code X-Request-Id} the gateway forwarded; create one if it
 *       is missing or malformed (direct calls in tests).
 *   <li>Gateway ({@code trustIncoming = false}): always create a new ID and replace any client-sent header, so the
 *       forwarded request carries the gateway's ID.
 * </ul>
 *
 * The ID goes into the MDC key {@code requestId} (log pattern) and the {@code X-Request-Id} response header. One log
 * line per request records method, path, status and duration (no query string, no body).
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final boolean trustIncoming;

    public RequestIdFilter(boolean trustIncoming) {
        this.trustIncoming = trustIncoming;
    }

    /** The current request's ID, or a fresh one when called outside a request. */
    public static String currentRequestId() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId =
                trustIncoming && incoming != null && SAFE_ID.matcher(incoming).matches()
                        ? incoming
                        : UUID.randomUUID().toString();
        long start = System.nanoTime();
        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER, requestId);
            chain.doFilter(new RequestIdRequest(request, requestId), response);
        } finally {
            // Docker's health check calls /actuator/health every 5 s on every service; do not log those.
            if (!request.getRequestURI().startsWith("/actuator/health")) {
                logRequest(request, response, start);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private static void logRequest(HttpServletRequest request, HttpServletResponse response, long start) {
        log.info(
                "{} {} -> {} ({} ms)",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                (System.nanoTime() - start) / 1_000_000);
    }

    /** Makes {@code X-Request-Id} return this request's ID, whatever the client sent. */
    private static final class RequestIdRequest extends HttpServletRequestWrapper {

        private final String requestId;

        RequestIdRequest(HttpServletRequest request, String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? requestId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(Set.of(requestId)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                String name = original.nextElement();
                if (!HEADER.equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            names.add(HEADER);
            return Collections.enumeration(names);
        }
    }
}
