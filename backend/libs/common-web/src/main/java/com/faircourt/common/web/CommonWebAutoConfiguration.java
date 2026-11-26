package com.faircourt.common.web;

import com.faircourt.common.identity.CallerIdentityArgumentResolver;
import com.faircourt.common.identity.IdentityToken;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    /** Identity token signing and checking; only in an application that sets the secret (gateway, playmatch). */
    @Bean
    @ConditionalOnProperty(IdentityToken.SECRET_PROPERTY)
    IdentityToken identityToken(
            @Value("${" + IdentityToken.SECRET_PROPERTY + "}") String secret, ObjectProvider<Clock> clock) {
        return new IdentityToken(secret, clock.getIfAvailable(Clock::systemUTC));
    }

    /** Lets a controller take a {@code CallerIdentity} argument, built only from the identity token header. */
    @Bean
    @ConditionalOnProperty(IdentityToken.SECRET_PROPERTY)
    WebMvcConfigurer callerIdentityResolver(IdentityToken identityToken) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerIdentityArgumentResolver(identityToken));
            }
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
