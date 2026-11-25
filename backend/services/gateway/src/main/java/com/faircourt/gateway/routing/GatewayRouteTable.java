package com.faircourt.gateway.routing;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * The gateway's whole route table (AD-14): deny by default; only the routes listed here exist. Feature routes are added
 * here as features land. Internal routes ({@code /internal/**}) can never be listed: {@link GatewayRoute} accepts only
 * {@code /api/v1/}.
 */
public record GatewayRouteTable(List<GatewayRoute> routes) {

    public GatewayRouteTable {
        routes = List.copyOf(routes);
    }

    /**
     * @param playmatchUrl base URL of playmatch-service ({@code faircourt.services.playmatch-url})
     * @param authUrl base URL of auth-service ({@code faircourt.services.auth-url})
     */
    public static GatewayRouteTable production(String playmatchUrl, String authUrl) {
        return new GatewayRouteTable(List.of(
                GatewayRoute.local("csrf-bootstrap", HttpMethod.GET, "/api/v1/auth/csrf", RouteAccess.PUBLIC),
                // Account login (blueprint sections 11, 13): answered by the gateway; the signed-in state is its
                // session.
                GatewayRoute.local("login", HttpMethod.POST, "/api/v1/auth/login", RouteAccess.PUBLIC),
                GatewayRoute.local("logout", HttpMethod.POST, "/api/v1/auth/logout", RouteAccess.ACCOUNT),
                GatewayRoute.local("me", HttpMethod.GET, "/api/v1/me", RouteAccess.ACCOUNT),
                // Password reset (owner approved 2026-11-18): anyone, POST only, forwarded to auth-service (CSRF token
                // still required).
                new GatewayRoute(
                        "password-forgot",
                        Set.of(HttpMethod.POST),
                        "/api/v1/auth/password/forgot",
                        RouteAccess.PUBLIC,
                        authUrl),
                new GatewayRoute(
                        "password-reset",
                        Set.of(HttpMethod.POST),
                        "/api/v1/auth/password/reset",
                        RouteAccess.PUBLIC,
                        authUrl),
                // Guest check-in (owner approved 2026-11-18): anyone, POST only, answered by the gateway itself (it
                // owns the guest cookie); CSRF token required.
                GatewayRoute.local(
                        "guest-check-in", HttpMethod.POST, "/api/v1/public/games/*/guests", RouteAccess.PUBLIC),
                // Member join and "my game" (blueprint section 13; My attendance design): answered by the gateway.
                // Join needs a signed-in account (CSRF token required); /me is for an account or a guest cookie.
                GatewayRoute.local("member-join", HttpMethod.POST, "/api/v1/games/*/join", RouteAccess.ACCOUNT),
                GatewayRoute.local("my-attendance", HttpMethod.GET, "/api/v1/games/*/me", RouteAccess.PLAYER),
                // Public game page (blueprint sections 3, 13; OD-22): anyone, read-only.
                new GatewayRoute(
                        "public-game",
                        Set.of(HttpMethod.GET),
                        "/api/v1/public/games/*",
                        RouteAccess.PUBLIC,
                        playmatchUrl)));
    }
}
