package com.faircourt.player.guest;

import java.util.UUID;

/** Answer of the internal guest-profile route: only the new profile's id. */
public record GuestProfileResponse(UUID playerId) {}
