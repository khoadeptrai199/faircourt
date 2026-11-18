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
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(
                http,
                auth -> auth.requestMatchers(HttpMethod.GET, "/api/v1/public/games/*")
                        .permitAll());
    }
}
