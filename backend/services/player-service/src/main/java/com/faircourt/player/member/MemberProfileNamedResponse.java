package com.faircourt.player.member;

import java.util.UUID;

/** Answer of the find-or-create route: the profile's id and its own display name (kept when the profile existed). */
public record MemberProfileNamedResponse(UUID playerId, String displayName) {}
