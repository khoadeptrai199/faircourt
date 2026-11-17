package com.faircourt.common.web;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security baseline shared by every backend service behind the gateway (API foundation design section 6; AD-14).
 *
 * <ul>
 *   <li>Health is public; each feature opens its own routes through {@code featureRoutes}; everything else is denied.
 *   <li>Failures are JSON ({@link JsonSecurityHandlers}).
 *   <li>Stateless: services never create an HTTP session.
 *   <li>No CSRF check here: services are not browser-facing (no host port), the gateway strips browser cookies before
 *       forwarding, and the gateway checks CSRF on every write (privacy-auth rule: CSRF protects cookie-authenticated
 *       writes, which only exist at the gateway).
 * </ul>
 */
public final class ServiceSecurityDefaults {

    private ServiceSecurityDefaults() {}

    public static SecurityFilterChain build(
            HttpSecurity http,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>
                    featureRoutes)
            throws Exception {
        JsonSecurityHandlers handlers = new JsonSecurityHandlers();
        http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**")
                            .permitAll();
                    featureRoutes.customize(auth);
                    auth.anyRequest().denyAll();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers));
        return http.build();
    }

    /** Baseline with no feature route open yet. */
    public static SecurityFilterChain build(HttpSecurity http) throws Exception {
        return build(http, auth -> {});
    }
}
