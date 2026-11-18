package com.faircourt.gateway.account;

import com.faircourt.common.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends a session after the account's password was changed (Password reset design D-5; OWASP Forgot Password: invalidate
 * sessions after a reset). Sessions live in the gateway's memory, so the gateway asks auth-service at most once per
 * interval (one minute) per signed-in session. If auth-service does not answer the session is kept (the idle and
 * absolute timeouts still apply) and the question is asked again shortly.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    /** Session attribute holding the {@link Instant} of the last successful check. */
    static final String CHECKED_AT = "fc.checkedAt";

    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(10);

    private final Clock clock;
    private final AuthClient authClient;
    private final Duration interval;

    public SessionRevalidationFilter(Clock clock, AuthClient authClient, Duration interval) {
        this.clock = clock;
        this.authClient = authClient;
        this.interval = interval;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session != null
                && authentication != null
                && authentication.getPrincipal() instanceof AccountResponse account
                && session.getAttribute(AbsoluteSessionTimeoutFilter.LOGIN_AT) instanceof Instant loginAt) {
            Instant now = clock.instant();
            Instant checkedAt = session.getAttribute(CHECKED_AT) instanceof Instant at ? at : loginAt;
            if (!checkedAt.plus(interval).isAfter(now)) {
                revalidate(session, account, loginAt, now);
            }
        }
        chain.doFilter(request, response);
    }

    private void revalidate(HttpSession session, AccountResponse account, Instant loginAt, Instant now) {
        try {
            if (authClient.sessionCheck(account.userId(), loginAt)) {
                session.setAttribute(CHECKED_AT, now);
            } else {
                session.invalidate();
                // This request is treated as anonymous from here on (answer 401).
                SecurityContextHolder.clearContext();
            }
        } catch (ApiException e) {
            // auth-service does not answer: keep the session and ask again soon.
            session.setAttribute(CHECKED_AT, now.minus(interval).plus(RETRY_AFTER_FAILURE));
        }
    }
}
