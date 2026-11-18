package com.faircourt.gateway.account;

/** Body of {@code POST /api/v1/auth/login}. Checked by hand in the controller (no extra validation dependency). */
public record LoginRequest(String email, String password) {}
