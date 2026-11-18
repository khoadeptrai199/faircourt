package com.faircourt.gateway.account;

import java.io.Serializable;
import java.util.UUID;

/**
 * The signed-in person's own identity: the answer to login and {@code GET /me}, and the principal kept in the gateway
 * session. Never carries a password or hash.
 */
public record AccountResponse(UUID userId, String email) implements Serializable {}
