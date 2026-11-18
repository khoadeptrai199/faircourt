package com.faircourt.auth.passwordreset;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forgot password, two anonymous steps (blueprint section 15; owner approved 2026-11-18). The gateway forwards both
 * routes here. Nothing in any answer says whether an email has an account.
 */
@RestController
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    /** Always 202 with an empty JSON object, whatever the email is. */
    @PostMapping(value = "/api/v1/auth/password/forgot", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        service.requestCode(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of());
    }

    /** 204 when the password was changed; 401 with the same body for every kind of bad code. */
    @PostMapping(value = "/api/v1/auth/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        if (!service.reset(request.email(), request.code(), request.newPassword())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
