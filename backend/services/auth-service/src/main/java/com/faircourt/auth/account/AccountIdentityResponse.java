package com.faircourt.auth.account;

import java.util.UUID;

/** Who the credentials belong to. Never carries the password hash or anything about other accounts. */
public record AccountIdentityResponse(UUID userId, String email) {}
