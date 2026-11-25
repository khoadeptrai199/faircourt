package com.faircourt.playmatch.attendance;

/** Answer of the own-attendance read to the gateway: only the caller's own state (blueprint section 13). */
public record MyAttendanceResponse(AttendanceState attendanceState) {}
