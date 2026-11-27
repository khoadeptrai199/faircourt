package com.faircourt.playmatch.match;

import java.util.UUID;

/** Host-only answer of start, finish and cancel: ids, numbers and state of this one match (design 3). */
public record HostMatchStateResponse(UUID matchId, int matchNo, int courtNo, MatchState state) {}
