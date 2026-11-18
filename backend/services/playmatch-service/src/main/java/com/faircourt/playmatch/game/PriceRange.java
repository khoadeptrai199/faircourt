package com.faircourt.playmatch.game;

/** What one player pays for the game, as a range in VND; {@code minVnd == maxVnd} means one price (OD-18). */
public record PriceRange(int minVnd, int maxVnd) {

    /** Null when neither bound is set; when only one bound is set, it is used as both. */
    static PriceRange of(Integer minVnd, Integer maxVnd) {
        if (minVnd == null && maxVnd == null) {
            return null;
        }
        int min = minVnd != null ? minVnd : maxVnd;
        int max = maxVnd != null ? maxVnd : minVnd;
        return new PriceRange(min, max);
    }
}
