package com.faircourt.player.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of the internal route {@code POST /internal/v1/players/members} (playmatch-service is the only caller).
 * {@code userId} is the account the gateway resolved from its session; {@code displayName} is the name chosen by the
 * gateway (My attendance design D-1).
 */
public record MemberProfileRequest(
        @NotNull UUID userId, @NotBlank @Size(max = 200) String displayName) {}
