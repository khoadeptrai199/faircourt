package com.faircourt.player.security;

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
 *   <li>Guest check-in: {@code POST /internal/v1/players/guests}, internal, called by playmatch-service only (AD-15).
 *       The service has no host port, so only containers on the Compose network can reach it.
 *   <li>Member profile (My attendance): {@code POST /internal/v1/players/members} and {@code GET
 *       /internal/v1/players/by-user/*}, internal, the same way.
 *   <li>Player levels (Host board): {@code POST /internal/v1/players/levels}, internal, the same way.
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(http, auth -> {
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/players/guests").permitAll();
            // Member join and own attendance: internal, called by playmatch-service only (AD-15).
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/players/members")
                    .permitAll();
            auth.requestMatchers(HttpMethod.GET, "/internal/v1/players/by-user/*")
                    .permitAll();
            // Private levels for the host board (Host board): internal, called by playmatch-service only (AD-15).
            auth.requestMatchers(HttpMethod.POST, "/internal/v1/players/levels").permitAll();
        });
    }
}
