package com.faircourt.auth.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Credentials the gateway forwards from the login form. Lengths are checked before any hashing work. */
public record AccountVerifyRequest(
        @NotBlank @Size(max = 254) String email,
        @NotEmpty @Size(max = 128) String password) {}
