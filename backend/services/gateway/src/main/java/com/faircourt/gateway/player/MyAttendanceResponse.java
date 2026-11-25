package com.faircourt.gateway.player;

import com.faircourt.gateway.guest.AttendanceState;

/**
 * Answer of {@code GET /api/v1/games/{gameId}/me}: only the caller's own state and the assignment (blueprint section
 * 13). {@code assignment} is always {@code null} until the own-assignment feature fills it. No id, name or band.
 */
public record MyAttendanceResponse(AttendanceState attendanceState, Object assignment) {}
