package com.faircourt.auth.security;

import com.faircourt.common.web.ServiceSecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared service baseline (API foundation, AD-14): health public, JSON errors, everything else denied unless a
 * feature opens its route here.
 *
 * <ul>
 *   <li>Account login: {@code POST /internal/v1/auth/verify}, internal, called by the gateway only. The service has no
 *       host port, so only containers on the Compose network can reach it (AD-15).
 *   <li>Password reset: {@code POST /api/v1/auth/password/forgot} and {@code POST /api/v1/auth/password/reset}
 *       (anyone; the gateway forwards them and checks CSRF) and {@code GET
 *       /internal/v1/auth/accounts/{userId}/session-check} (internal, called by the gateway).
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(http, auth -> {
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/auth/verify").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset")
                    .permitAll();
            auth.requestMatchers(HttpMethod.GET, "/internal/v1/auth/accounts/*/session-check")
                    .permitAll();
        });
    }
}
