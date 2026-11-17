package com.faircourt.gateway.routing;

import java.util.List;
import org.springframework.http.HttpMethod;

/**
 * The gateway's whole route table (AD-14): deny by default; only the routes listed here exist. Feature routes are added
 * here as features land (e.g. Public session page adds {@code /api/v1/public/sessions/*} to playmatch-service).
 * Internal routes ({@code /internal/**}) can never be listed: {@link GatewayRoute} accepts only {@code /api/v1/}.
 */
public record GatewayRouteTable(List<GatewayRoute> routes) {

    public GatewayRouteTable {
        routes = List.copyOf(routes);
    }

    public static GatewayRouteTable production() {
        return new GatewayRouteTable(
                List.of(GatewayRoute.local("csrf-bootstrap", HttpMethod.GET, "/api/v1/auth/csrf", RouteAccess.PUBLIC)));
    }
}
