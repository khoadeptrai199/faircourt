package com.faircourt.gateway.routing;

import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * One line of the gateway route table (AD-14): which HTTP methods on which path, who may call it, and where it goes.
 * {@code targetUrl == null} means the gateway answers the route itself (e.g. the CSRF bootstrap). The same line drives
 * both the security rule and the proxy route, so a route cannot be forwarded without an access rule, and a read-only
 * route cannot be used for writes.
 *
 * <p>{@code audience} is the target service's name ({@code spring.application.name}); the identity token of a
 * forwarded {@link RouteAccess#HOST} route is issued for it only (Attendance management design 3.2). It is
 * {@code null} for local routes.
 */
public record GatewayRoute(
        String id, Set<HttpMethod> methods, String pathPattern, RouteAccess access, String targetUrl, String audience) {

    public GatewayRoute {
        if (!pathPattern.startsWith("/api/v1/")) {
            throw new IllegalArgumentException("Gateway routes must be under /api/v1/: " + pathPattern);
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Gateway route needs at least one HTTP method: " + id);
        }
        if (targetUrl != null && access == RouteAccess.HOST && audience == null) {
            throw new IllegalArgumentException("A forwarded host route needs an audience: " + id);
        }
        methods = Set.copyOf(methods);
    }

    /** A forwarded route without an identity token (public routes); no audience. */
    public GatewayRoute(String id, Set<HttpMethod> methods, String pathPattern, RouteAccess access, String targetUrl) {
        this(id, methods, pathPattern, access, targetUrl, null);
    }

    public static GatewayRoute local(String id, HttpMethod method, String pathPattern, RouteAccess access) {
        return new GatewayRoute(id, Set.of(method), pathPattern, access, null, null);
    }

    public boolean forwarded() {
        return targetUrl != null;
    }
}
