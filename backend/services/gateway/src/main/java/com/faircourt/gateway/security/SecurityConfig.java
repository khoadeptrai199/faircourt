package com.faircourt.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * F18 baseline: only the health endpoint is reachable; every other route is denied until F01/F02 add the
 * route table, CSRF bootstrap and request ID (AD-14). CSRF stays enabled (Spring Security default).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .denyAll());
        return http.build();
    }
}
