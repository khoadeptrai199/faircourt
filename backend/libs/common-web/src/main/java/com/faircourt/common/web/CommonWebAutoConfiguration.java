package com.faircourt.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers the shared error handler and the request ID filter in every service and the gateway (AD-18).
 * {@code faircourt.request-id.trust-incoming} defaults to {@code false} (fail closed: a new ID per request); services
 * behind the gateway set it to {@code true} to keep the gateway's ID.
 */
@AutoConfiguration
public class CommonWebAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
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
