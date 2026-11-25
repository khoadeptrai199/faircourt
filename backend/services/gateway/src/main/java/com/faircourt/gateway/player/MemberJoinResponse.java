package com.faircourt.gateway.player;

import com.faircourt.gateway.guest.AttendanceState;

/** Answer of a member join: only the attendance state (My attendance design section 3). No id, name or band. */
public record MemberJoinResponse(AttendanceState attendanceState) {}
