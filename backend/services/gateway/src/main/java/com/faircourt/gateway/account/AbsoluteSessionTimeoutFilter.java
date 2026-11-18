package com.faircourt.gateway.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a login after a fixed time however active the person is (OWASP Session Management Cheat Sheet: enforce an
 * absolute timeout as well as an idle timeout, both on the server). The idle timeout is the servlet session timeout.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    /** Session attribute holding the {@link Instant} of the login. */
    public static final String LOGIN_AT = "fc.loginAt";

    private final Clock clock;
    private final Duration maxAge;

    public AbsoluteSessionTimeoutFilter(Clock clock, Duration maxAge) {
        this.clock = clock;
        this.maxAge = maxAge;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(LOGIN_AT) instanceof Instant loginAt) {
            if (clock.instant().isAfter(loginAt.plus(maxAge))) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
