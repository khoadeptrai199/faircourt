package com.faircourt.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Registers the shared error handler and the request ID filter in every service and the gateway (AD-18).
 * {@code faircourt.request-id.trust-incoming} defaults to {@code false} (fail closed: a new ID per request); services
 * behind the gateway set it to {@code true} to keep the gateway's ID.
 *
 * <p>It also registers a {@link UserDetailsService} with no users, so Spring Boot does not create its default user and
 * print a generated password in the logs. A feature that adds real login defines its own bean, which replaces this one.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class CommonWebAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService noLocalUsers() {
        return username -> {
            throw new UsernameNotFoundException("No local users");
        };
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter(
            @Value("${faircourt.request-id.trust-incoming:false}") boolean trustIncoming) {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(new RequestIdFilter(trustIncoming));
        // Before Spring Security, so security error bodies and log lines carry the ID too.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
