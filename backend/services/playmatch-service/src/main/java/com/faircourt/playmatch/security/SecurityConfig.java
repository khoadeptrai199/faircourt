package com.faircourt.playmatch.security;

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
 *   <li>Public game page: {@code GET /api/v1/public/games/*} for anyone, GET only (blueprint sections 3, 13).
 *   <li>Guest check-in: {@code POST /internal/v1/games/by-code/*}{@code /guest-check-ins}, internal, gateway only.
 *   <li>Member join and own attendance (My attendance): {@code POST /internal/v1/games/*}{@code /member-attendances} and
 *       {@code GET /internal/v1/games/*}{@code /my-attendance}, internal, gateway only.
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(http, auth -> {
            auth.requestMatchers(HttpMethod.GET, "/api/v1/public/games/*").permitAll();
            // Guest check-in: internal, called by the gateway only (AD-15).
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/games/by-code/*/guest-check-ins")
                    .permitAll();
            // Member join and own attendance: internal, called by the gateway only (AD-15).
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/games/*/member-attendances")
                    .permitAll();
            auth.requestMatchers(HttpMethod.GET, "/internal/v1/games/*/my-attendance")
                    .permitAll();
            // Host board and games I host: the gateway only forwards signed-in accounts; the identity token and
            // AccessPolicy decide in the controller (401 / 403 / 404).
            auth.requestMatchers(HttpMethod.GET, "/api/v1/host/games/*/board").permitAll();
            auth.requestMatchers(HttpMethod.GET, "/api/v1/host/games").permitAll();
            // Host attendance change (Attendance management): same rule; the token is bound to this method and path.
            auth.requestMatchers(HttpMethod.PATCH, "/api/v1/host/games/*/attendance/*")
                    .permitAll();
        });
    }
}
