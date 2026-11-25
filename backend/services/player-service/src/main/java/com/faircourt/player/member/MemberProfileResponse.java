package com.faircourt.player.member;

import java.util.UUID;

/** Answer of the internal member-profile routes: only the profile's id. */
public record MemberProfileResponse(UUID playerId) {}
