package com.faircourt.player.level;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of the internal route {@code POST /internal/v1/players/levels} (Host board design 3.3): 1 to 200 player ids.
 * playmatch-service is the only caller.
 */
public record PlayerLevelsRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> playerIds) {}
