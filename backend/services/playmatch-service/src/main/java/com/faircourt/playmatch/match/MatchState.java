package com.faircourt.playmatch.match;

/**
 * State of a match; constants equal the DB check values (blueprint section 11). Match states: {@code PLANNED -> ACTIVE
 * -> FINISHED}, with {@code PLANNED -> CANCELLED} and {@code ACTIVE -> CANCELLED} (AD-03). {@code FINISHED} and
 * {@code CANCELLED} are final.
 */
public enum MatchState {
    PLANNED,
    ACTIVE,
    FINISHED,
    CANCELLED;

    public boolean canStart() {
        return this == PLANNED;
    }

    public boolean canFinish() {
        return this == ACTIVE;
    }

    public boolean canCancel() {
        return this == PLANNED || this == ACTIVE;
    }
}
