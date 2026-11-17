package com.faircourt.gateway.csrf;

/** Body of {@code GET /api/v1/auth/csrf}. */
public record CsrfTokenResponse(String headerName, String token) {}
