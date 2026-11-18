package com.faircourt.gateway.guest;

/** Attendance states as playmatch-service sends them (same constants as its enum; the gateway keeps its own copy). */
public enum AttendanceState {
    PENDING,
    READY,
    ASSIGNED,
    PLAYING,
    RESTING,
    LEFT
}
