package com.faircourt.common.web.contract;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.ServiceSecurityDefaults;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only service that uses common-web exactly like a real service, with one endpoint per error type. */
@SpringBootApplication
public class ContractTestApplication {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ServiceSecurityDefaults.build(
                http, auth -> auth.requestMatchers("/api/v1/test/**").permitAll());
    }

    record NameRequest(@NotBlank String name) {}

    @RestController
    @RequestMapping("/api/v1/test")
    static class ErrorController {

        @GetMapping("/api-exception")
        String apiException() {
            throw new ApiException(ErrorCode.MATCH_CONFLICT);
        }

        @GetMapping("/unavailable")
        String unavailable() {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }

        @PostMapping("/validated")
        String validated(@Valid @RequestBody NameRequest request) {
            return "ok";
        }

        @GetMapping("/typed")
        String typed(@RequestParam int n) {
            return "ok";
        }

        @GetMapping("/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint secret_sql");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/status-conflict")
        String statusConflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "secret reason");
        }

        @GetMapping("/status-not-found")
        String statusNotFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "secret reason");
        }

        @GetMapping("/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("secret reason");
        }

        @GetMapping("/not-authenticated")
        String notAuthenticated() {
            throw new InsufficientAuthenticationException("secret reason");
        }

        @GetMapping("/get-only")
        String getOnly() {
            return "ok";
        }
    }
}
