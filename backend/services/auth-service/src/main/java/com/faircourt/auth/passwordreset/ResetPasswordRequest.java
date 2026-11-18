package com.faircourt.auth.passwordreset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/password/reset}. The "repeat the password" entry exists only in the form. The new
 * password rules are checked in {@link PasswordRules}.
 */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{8}") String code,
        @NotEmpty @Size(max = 128) String newPassword) {}
