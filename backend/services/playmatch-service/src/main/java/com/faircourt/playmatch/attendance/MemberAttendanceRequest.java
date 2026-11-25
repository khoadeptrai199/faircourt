package com.faircourt.playmatch.attendance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of the internal route {@code POST /internal/v1/games/{gameId}/member-attendances} (the gateway is the only
 * caller). {@code userId} is the account the gateway resolved from its session; it is never a claim of the browser.
 * {@code displayName} is the name the gateway chose (My attendance design D-1).
 */
public record MemberAttendanceRequest(
        @NotNull UUID userId, @NotBlank @Size(max = 200) String displayName) {}
