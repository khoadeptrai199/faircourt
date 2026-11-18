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
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(
                http,
                auth -> auth.requestMatchers(HttpMethod.POST, "/internal/v1/auth/verify")
                        .permitAll());
    }
}
