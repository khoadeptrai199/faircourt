package com.faircourt.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Slice 1 baseline: only the health endpoint is reachable; every other route is denied
 * until the identity slice (blueprint section 17, step 2) adds login, CSRF bootstrap and API rules.
 * CSRF stays enabled (Spring Security default).
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

    /** Password hashes through Spring Security's encoder (blueprint section 15). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
