package com.faircourt.player.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of the internal route {@code POST /internal/v1/players/guests} (playmatch-service is the only caller). The
 * answers are checked by {@link GuestAnswers#parse}: all four 0-3, or all four {@code null}, or {@code answers} itself
 * {@code null}.
 */
public record CreateGuestProfileRequest(
        @NotBlank @Size(max = 80) String displayName,
        @NotNull UUID gameId,
        RawAnswers answers) {

    /** The answers as sent; nullable so that "all unknown" and "partial" can be told apart. */
    public record RawAnswers(Integer serve, Integer rally, Integer movement, Integer positioning) {}
}
