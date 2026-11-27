package com.faircourt.playmatch.attendance;

/**
 * State of an attendance; constants equal the DB check values (blueprint section 11). The transition table for the
 * host's manual changes lives here (AD-03; Attendance management design 5); the match commands add their own
 * transitions with the match features.
 */
public enum AttendanceState {
    PENDING,
    READY,
    ASSIGNED,
    PLAYING,
    RESTING,
    LEFT;

    /**
     * The host may set only {@code READY} (approve) or {@code LEFT} (went home, or reject) by hand. {@code RESTING} is
     * not offered (owner 2026-11-26); the others are owned by check-in and the match commands.
     */
    public boolean isHostTarget() {
        return this == READY || this == LEFT;
    }

    /** True when the player is in a planned or active match; the host must cancel or finish that match first. */
    public boolean inMatch() {
        return this == ASSIGNED || this == PLAYING;
    }

    /**
     * Only a {@code READY} player can be put into a planned match ({@code READY -> ASSIGNED}, blueprint sections 11,
     * 12); the match commands use this check, not the host's manual moves.
     */
    public boolean canBeAssigned() {
        return this == READY;
    }

    /**
     * Whether the host may move a player from this state to {@code target}: exactly {@code PENDING -> READY},
     * {@code PENDING -> LEFT} and {@code READY -> LEFT}. Staying in the same state is not a move (the caller treats it
     * as "no change").
     */
    public boolean hostMayMoveTo(AttendanceState target) {
        return switch (this) {
            case PENDING -> target == READY || target == LEFT;
            case READY -> target == LEFT;
            case ASSIGNED, PLAYING, RESTING, LEFT -> false;
        };
    }
}
