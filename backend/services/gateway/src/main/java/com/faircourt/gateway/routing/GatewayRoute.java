package com.faircourt.gateway.routing;

import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * One line of the gateway route table (AD-14): which HTTP methods on which path, who may call it, and where it goes.
 * {@code targetUrl == null} means the gateway answers the route itself (e.g. the CSRF bootstrap). The same line drives
 * both the security rule and the proxy route, so a route cannot be forwarded without an access rule, and a read-only
 * route cannot be used for writes.
 */
public record GatewayRoute(
        String id, Set<HttpMethod> methods, String pathPattern, RouteAccess access, String targetUrl) {

    public GatewayRoute {
        if (!pathPattern.startsWith("/api/v1/")) {
            throw new IllegalArgumentException("Gateway routes must be under /api/v1/: " + pathPattern);
        }
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("Gateway route needs at least one HTTP method: " + id);
        }
        methods = Set.copyOf(methods);
    }

    public static GatewayRoute local(String id, HttpMethod method, String pathPattern, RouteAccess access) {
        return new GatewayRoute(id, Set.of(method), pathPattern, access, null);
    }

    public boolean forwarded() {
        return targetUrl != null;
    }
}
