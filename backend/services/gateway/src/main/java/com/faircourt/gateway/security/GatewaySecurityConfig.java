package com.faircourt.gateway.security;

import com.faircourt.common.web.JsonSecurityHandlers;
import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.GatewayRouteTable;
import com.faircourt.gateway.routing.RouteAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * First gate for every browser request (blueprint section 15; AD-14).
 *
 * <ul>
 *   <li>Health is public; each route in the {@link GatewayRouteTable} gets its access rule; everything else is denied
 *       (anonymous → 401 JSON, so unknown routes are not revealed).
 *   <li>CSRF stays enabled for every write (blueprint section 0); the token lives in the gateway's HTTP session
 *       (owner decision 2026-11-17) and is bootstrapped by {@code GET /api/v1/auth/csrf}.
 *   <li>No CORS configuration: the browser reaches the gateway on the same origin through the Vite proxy.
 * </ul>
 */
@Configuration
public class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayRouteTable table) throws Exception {
        JsonSecurityHandlers handlers = new JsonSecurityHandlers();
        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll();
                    for (GatewayRoute route : table.routes()) {
                        if (route.access() == RouteAccess.PUBLIC) {
                            for (HttpMethod method : route.methods()) {
                                auth.requestMatchers(method, route.pathPattern())
                                        .permitAll();
                            }
                        }
                    }
                    auth.anyRequest().denyAll();
                })
                // API only: never save a denied request into a new session (no login redirect to replay).
                .requestCache(cache -> cache.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));
        return http.build();
    }
}
