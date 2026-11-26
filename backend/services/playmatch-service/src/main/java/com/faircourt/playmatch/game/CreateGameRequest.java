package com.faircourt.playmatch.game;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Body of {@code POST /api/v1/games} (blueprint sections 12, 13; Create game design 3.1). There is deliberately no host
 * field: the host is the caller (section 13). Sizes after trimming and the cross-field rules (end after start, time
 * window, min not above max) are checked in {@link GameService}.
 */
public record CreateGameRequest(
        @NotBlank String title,
        @NotBlank String venue,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @NotNull @Min(1) @Max(4) Integer courtCount,
        @NotNull MatchFormat matchFormat,
        @NotNull @Min(1) @Max(20) Integer minSets,
        @Valid Price pricePerPerson) {

    /** Largest price bound the form accepts, in VND. */
    public static final int MAX_PRICE_VND = 10_000_000;

    /** Optional price per person by gender; a missing line stays unset. */
    public record Price(@Valid Line male, @Valid Line female) {}

    /** One price line; one bound alone is used as both bounds ({@link PriceRange#of}). */
    public record Line(
            @Min(0) @Max(MAX_PRICE_VND) Integer minVnd,
            @Min(0) @Max(MAX_PRICE_VND) Integer maxVnd) {}
}
