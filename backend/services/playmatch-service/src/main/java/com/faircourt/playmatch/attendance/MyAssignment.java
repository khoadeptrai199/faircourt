package com.faircourt.playmatch.attendance;

import com.faircourt.playmatch.match.MatchState;

/**
 * The caller's own unreleased match (blueprint section 13): its number, court and state. Only {@code PLANNED} or
 * {@code ACTIVE} can occur, because finish and cancel release the players. No other player, team, band or id.
 */
public record MyAssignment(int matchNo, int courtNo, MatchState matchState) {}
