package com.faircourt.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.methods;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** Builds the Spring Cloud Gateway (Web MVC) routes from the {@link GatewayRouteTable}. */
@Configuration
public class GatewayRoutes {

    @Bean
    GatewayRouteTable gatewayRouteTable(
            @Value("${faircourt.services.playmatch-url}") String playmatchUrl,
            @Value("${faircourt.services.auth-url}") String authUrl) {
        return GatewayRouteTable.production(playmatchUrl, authUrl);
    }

    @Bean
    RouterFunction<ServerResponse> gatewayRouterFunction(GatewayRouteTable table) {
        RouterFunction<ServerResponse> all = request -> Optional.empty();
        for (GatewayRoute r : table.routes()) {
            if (!r.forwarded()) {
                continue;
            }
            all = all.and(route(r.id())
                    .route(path(r.pathPattern()).and(methods(r.methods().toArray(HttpMethod[]::new))), http())
                    .before(uri(r.targetUrl()))
                    .before(new HeaderSanitizer())
                    .build());
        }
        return all;
    }
}
