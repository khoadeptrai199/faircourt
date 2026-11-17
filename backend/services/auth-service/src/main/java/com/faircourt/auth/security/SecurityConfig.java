package com.faircourt.auth.security;

import com.faircourt.common.web.ServiceSecurityDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared service baseline (API foundation, AD-14): health public, JSON errors, everything else denied until a feature
 * opens its route here.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(http);
    }
}
