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
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(
                http,
                auth -> auth.requestMatchers(HttpMethod.POST, "/internal/v1/players/guests")
                        .permitAll());
    }
}
