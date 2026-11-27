package com.faircourt.playmatch.attendance;

/**
 * Answer of the own-attendance read to the gateway: the caller's own state and, while the caller is in an unreleased
 * match, that match's number, court and state; {@code null} otherwise (blueprint section 13).
 */
public record MyAttendanceResponse(AttendanceState attendanceState, MyAssignment assignment) {}
