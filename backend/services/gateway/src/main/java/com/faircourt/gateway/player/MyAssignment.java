package com.faircourt.gateway.player;

/**
 * The caller's own unreleased match as playmatch-service answered it (blueprint section 13). {@code matchState} is
 * {@code PLANNED} or {@code ACTIVE}; the gateway refuses any other value. No other player, team, band or id.
 */
public record MyAssignment(int matchNo, int courtNo, String matchState) {}
