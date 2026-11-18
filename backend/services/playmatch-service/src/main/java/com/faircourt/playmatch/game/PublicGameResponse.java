package com.faircourt.playmatch.game;

import java.time.Instant;
import java.util.UUID;

/**
 * Public summary of one game for anyone with its link (blueprint sections 3, 11, 13). Public audience only: never
 * reused for players or the host, and never carries names, bands, roster, matches or guest tokens.
 *
 * <p>{@code gameId} is the opaque internal id; it grants nothing because every private route checks identity
 * (OD-02, owner 2026-11-18).
 */
public record PublicGameResponse(
        UUID gameId,
        String title,
        String venue,
        Instant startsAt,
        Instant endsAt,
        int courtCount,
        GameState state,
        MatchFormat matchFormat,
        int minSets,
        PricePerPerson pricePerPerson) {}
