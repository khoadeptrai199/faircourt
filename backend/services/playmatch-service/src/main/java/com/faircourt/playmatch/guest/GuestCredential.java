package com.faircourt.playmatch.guest;

import java.time.Instant;

/** The raw guest token (shown only to the gateway, which puts it in the cookie) and when it ends. */
public record GuestCredential(String token, Instant expiresAt) {}
