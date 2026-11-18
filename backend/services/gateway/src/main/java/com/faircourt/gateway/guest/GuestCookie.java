package com.faircourt.gateway.guest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The guest cookie (blueprint section 12; OWASP Session Management Cheat Sheet): HttpOnly, SameSite=Lax, {@code Secure}
 * from {@code COOKIE_SECURE}, Path=/, no Domain, ends with the credential. Generic name ({@code fc_guest}; on HTTPS set
 * {@code GUEST_COOKIE_NAME=__Host-fc_guest}, which needs Secure). JavaScript never sees it.
 */
@Component
public class GuestCookie {

    private final String name;
    private final boolean secure;
    private final Clock clock;

    public GuestCookie(
            @Value("${faircourt.guest.cookie-name}") String name,
            @Value("${faircourt.guest.cookie-secure}") boolean secure,
            Clock clock) {
        this.name = name;
        this.secure = secure;
        this.clock = clock;
    }

    public String name() {
        return name;
    }

    /** The cookie value the browser sent, if any (its shape is checked by the auth-service lookup). */
    public Optional<String> valueIn(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())
                    && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** A new cookie that replaces any older one of the same name; Max-Age runs until {@code expiresAt}. */
    public ResponseCookie issue(String token, Instant expiresAt) {
        Duration life = Duration.between(clock.instant(), expiresAt);
        long seconds = Math.max(1, life.toSeconds());
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(seconds)
                .build();
    }
}
