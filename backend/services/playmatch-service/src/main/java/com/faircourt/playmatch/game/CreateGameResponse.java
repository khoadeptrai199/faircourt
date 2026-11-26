package com.faircourt.playmatch.game;

import java.util.UUID;

/** Answer of the create-game route: only what the new host needs to open the board and share the page (design 3.1). */
public record CreateGameResponse(UUID gameId, String publicCode) {}
