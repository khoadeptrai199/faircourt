package com.faircourt.auth.account;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /internal/v1/auth/accounts/{userId}/session-check?since=<instant>}: internal route for the gateway
 * (AD-15). A session that logged in at {@code since} is still valid only if the account exists and its password did not
 * change after that moment (Password reset design D-5). Read-only.
 */
@RestController
public class SessionCheckController {

    private final JdbcClient jdbc;

    public SessionCheckController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/internal/v1/auth/accounts/{userId}/session-check")
    public Map<String, Boolean> check(@PathVariable UUID userId, @RequestParam Instant since) {
        Optional<OffsetDateTime> changedAt = jdbc.sql("SELECT password_changed_at FROM app_user WHERE id = :id")
                .param("id", userId)
                .query(OffsetDateTime.class)
                .optional();
        boolean valid = changedAt.isPresent() && !changedAt.get().toInstant().isAfter(since);
        return Map.of("valid", valid);
    }
}
