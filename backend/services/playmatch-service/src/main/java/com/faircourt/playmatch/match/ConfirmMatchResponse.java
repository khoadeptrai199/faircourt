package com.faircourt.playmatch.match;

import java.util.UUID;

/** Answer of the match confirm (blueprint section 13); host audience only. */
public record ConfirmMatchResponse(UUID matchId, int matchNo, int courtNo, MatchState state) {}
