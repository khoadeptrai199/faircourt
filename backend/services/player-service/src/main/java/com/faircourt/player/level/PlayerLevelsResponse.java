package com.faircourt.player.level;

import java.util.List;
import java.util.UUID;

/**
 * Answer of {@code POST /internal/v1/players/levels}: one entry per known player id, unknown ids left out. Private
 * levels: only for playmatch-service's host board (Host board design 3.3).
 */
public record PlayerLevelsResponse(List<Entry> players) {

    /** {@code kind} is {@code MEMBER} or {@code GUEST}; {@code band} is 1 to 4 or null; no name, user id or answers. */
    public record Entry(UUID playerId, String kind, Integer band, String bandStatus) {}
}
