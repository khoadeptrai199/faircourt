package com.faircourt.playmatch.attendance;

/** State of an attendance; constants equal the DB check values (blueprint section 11). */
public enum AttendanceState {
    PENDING,
    READY,
    ASSIGNED,
    PLAYING,
    RESTING,
    LEFT
}
