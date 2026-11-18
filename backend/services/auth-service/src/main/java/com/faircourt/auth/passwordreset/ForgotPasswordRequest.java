package com.faircourt.auth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/password/forgot}. */
public record ForgotPasswordRequest(
        @NotBlank @Size(max = 254) String email) {}
