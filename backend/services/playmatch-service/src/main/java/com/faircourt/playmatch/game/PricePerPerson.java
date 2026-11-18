package com.faircourt.playmatch.game;

/**
 * Price per person by gender, shown as information only; players pay the host outside the app (OD-18). A line is
 * {@code null} when the host did not set it. Players' gender is never stored or asked.
 */
public record PricePerPerson(PriceRange male, PriceRange female) {

    /** Null when no price line is set. */
    static PricePerPerson of(PriceRange male, PriceRange female) {
        return male == null && female == null ? null : new PricePerPerson(male, female);
    }
}
