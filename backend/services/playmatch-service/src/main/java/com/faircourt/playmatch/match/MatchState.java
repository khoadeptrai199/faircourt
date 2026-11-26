package com.faircourt.playmatch.match;

/** State of a match; constants equal the DB check values (blueprint section 11). Transitions come with the match features. */
public enum MatchState {
    PLANNED,
    ACTIVE,
    FINISHED,
    CANCELLED
}
