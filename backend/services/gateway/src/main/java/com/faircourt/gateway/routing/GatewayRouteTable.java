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

    /** @param playmatchUrl base URL of playmatch-service ({@code faircourt.services.playmatch-url}) */
    public static GatewayRouteTable production(String playmatchUrl) {
        return new GatewayRouteTable(List.of(
                GatewayRoute.local("csrf-bootstrap", HttpMethod.GET, "/api/v1/auth/csrf", RouteAccess.PUBLIC),
                // Public game page (blueprint sections 3, 13; OD-22): anyone, read-only.
                new GatewayRoute(
                        "public-game",
                        Set.of(HttpMethod.GET),
                        "/api/v1/public/games/*",
                        RouteAccess.PUBLIC,
                        playmatchUrl)));
    }
}
