package com.faircourt.gateway.account;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account login, logout and "who am I" (blueprint sections 11, 13, 15; owner-approved design). The signed-in state
 * lives only in the gateway's HTTP session; auth-service only checks the password.
 *
 * <p>Security rules applied here (OWASP Authentication and Session Management Cheat Sheets): one answer for every kind
 * of wrong credentials; attempts counted per email and slowed down ({@link LoginThrottle}); a new session id at login
 * (session fixation); the session is destroyed at logout; {@code Cache-Control: no-store} on every answer; JSON only
 * (a form post is refused); CSRF token required by the filter chain; no password, email or hash in any log line.
 */
@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    /** BCrypt reads at most 72 bytes; longer is refused, never cut (OWASP). Same limit in auth-service. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final AuthClient authClient;
    private final LoginThrottle throttle;
    private final SecurityContextRepository contextRepository;
    private final Clock clock;

    public AccountController(
            AuthClient authClient, LoginThrottle throttle, SecurityContextRepository contextRepository, Clock clock) {
        this.authClient = authClient;
        this.throttle = throttle;
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    @PostMapping(value = "/api/v1/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountResponse> login(
            @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        validate(body);
        String key = LoginThrottle.keyOf(body.email());

        // The attempt is counted before the password is checked, so parallel guesses cannot pass the limit.
        OptionalLong locked = throttle.tryAcquire(key);
        if (locked.isPresent()) {
            log.warn("Login refused: too many failed attempts for one account");
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultMessage(), (int)
                    locked.getAsLong());
        }

        AccountResponse account;
        try {
            account = authClient.verify(body.email().strip(), body.password());
        } catch (ApiException e) {
            if (e.code() == ErrorCode.UNAUTHENTICATED) {
                log.warn("Login failed: wrong credentials");
            } else {
                // No verdict on the password (service down, bad request): the attempt is not held against the account.
                throttle.release(key);
            }
            throw e;
        }
        throttle.recordSuccess(key);
        startSession(account, request, response);
        return noStore(account);
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        // Tell the browser to drop cookies and cached responses for this site (OWASP Session Management).
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("Clear-Site-Data", "\"cache\", \"cookies\"")
                .build();
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<AccountResponse> me(Authentication authentication) {
        return noStore((AccountResponse) authentication.getPrincipal());
    }

    private static void validate(LoginRequest body) {
        if (body == null
                || body.email() == null
                || body.email().isBlank()
                || body.email().length() > 254
                || body.password() == null
                || body.password().isEmpty()
                || body.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /** Session attribute under which Spring Security keeps the CSRF token (HttpSessionCsrfTokenRepository). */
    private static final String CSRF_ATTRIBUTE =
            "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";

    /** A new session id at login (session fixation); the old id stops working. */
    private void startSession(AccountResponse account, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        request.getSession().setAttribute(AbsoluteSessionTimeoutFilter.LOGIN_AT, clock.instant());
        // A new CSRF token for the new session (OWASP: regenerate the token at authentication); the client fetches it
        // again.
        request.getSession().removeAttribute(CSRF_ATTRIBUTE);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                account, null, List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private static ResponseEntity<AccountResponse> noStore(AccountResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
