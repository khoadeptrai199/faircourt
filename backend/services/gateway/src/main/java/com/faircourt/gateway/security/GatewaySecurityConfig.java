package com.faircourt.gateway.security;

import com.faircourt.common.web.JsonSecurityHandlers;
import com.faircourt.gateway.account.AbsoluteSessionTimeoutFilter;
import com.faircourt.gateway.account.AuthClient;
import com.faircourt.gateway.account.RequestBodyLimitFilter;
import com.faircourt.gateway.account.SessionRevalidationFilter;
import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.GatewayRouteTable;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * First gate for every browser request (blueprint section 15; AD-14).
 *
 * <ul>
 *   <li>Health is public; each route in the {@link GatewayRouteTable} gets its access rule ({@code PUBLIC}: anyone,
 *       {@code ACCOUNT}: a signed-in account); everything else is denied (anonymous → 401 JSON, so unknown routes are
 *       not revealed).
 *   <li>CSRF stays enabled for every write (blueprint section 0); the token lives in the gateway's HTTP session
 *       (owner decision 2026-11-17) and is bootstrapped by {@code GET /api/v1/auth/csrf}.
 *   <li>The signed-in account is kept in the HTTP session ({@link SecurityContextRepository}); a login also ends after an
 *       absolute time ({@link AbsoluteSessionTimeoutFilter}), on top of the idle timeout of the servlet session.
 *   <li>No CORS configuration: the browser reaches the gateway on the same origin through the Vite proxy.
 * </ul>
 */
@Configuration
public class GatewaySecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewayRouteTable table,
            SecurityContextRepository contextRepository,
            Clock clock,
            AuthClient authClient,
            @Value("${faircourt.account.absolute-timeout}") Duration absoluteTimeout,
            @Value("${faircourt.account.session-recheck-interval}") Duration recheckInterval)
            throws Exception {
        JsonSecurityHandlers handlers = new JsonSecurityHandlers();
        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll();
                    for (GatewayRoute route : table.routes()) {
                        for (HttpMethod method : route.methods()) {
                            switch (route.access()) {
                                case PUBLIC ->
                                    auth.requestMatchers(method, route.pathPattern())
                                            .permitAll();
                                case ACCOUNT ->
                                    auth.requestMatchers(method, route.pathPattern())
                                            .authenticated();
                            }
                        }
                    }
                    auth.anyRequest().denyAll();
                })
                // The signed-in account lives in the HTTP session; the login controller saves it there explicitly.
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // Ends a login after the absolute time; runs before the session's security context is read.
                .addFilterBefore(
                        new AbsoluteSessionTimeoutFilter(clock, absoluteTimeout), SecurityContextHolderFilter.class)
                // The anonymous login and password reset requests have a small body limit, checked before anything is
                // parsed.
                .addFilterBefore(
                        new RequestBodyLimitFilter(
                                "/api/v1/auth/login",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/public/games/*/guests"),
                        CsrfFilter.class)
                // After a password change the account's sessions end: asked of auth-service at most once per interval.
                .addFilterAfter(
                        new SessionRevalidationFilter(clock, authClient, recheckInterval),
                        SecurityContextHolderFilter.class)
                // API only: never save a denied request into a new session (no login redirect to replay).
                .requestCache(cache -> cache.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));
        return http.build();
    }
}
