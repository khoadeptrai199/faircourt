package com.faircourt.playmatch.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/host/games/{gameId}/matches} (blueprint section 13). The JSON names of the two teams are
 * {@code A} and {@code B}. Team sizes per game format are checked by {@link MatchLineup}.
 */
public record ConfirmMatchRequest(
        @NotNull @Min(1) @Max(4) Integer courtNo,
        @NotNull @Valid Teams teams) {

    public record Teams(
            @NotNull @Size(min = 1, max = 2) List<@NotNull UUID> A,
            @NotNull @Size(min = 1, max = 2) List<@NotNull UUID> B) {}
}
