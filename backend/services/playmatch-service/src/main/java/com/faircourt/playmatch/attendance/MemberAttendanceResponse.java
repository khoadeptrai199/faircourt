package com.faircourt.playmatch.attendance;

/** Answer of the member join to the gateway: the state now and whether this call created the entry. */
public record MemberAttendanceResponse(AttendanceState attendanceState, boolean created) {}
