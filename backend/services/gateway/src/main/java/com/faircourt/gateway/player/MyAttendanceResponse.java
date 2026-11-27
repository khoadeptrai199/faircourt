package com.faircourt.gateway.player;

import com.faircourt.gateway.guest.AttendanceState;

/**
 * Answer of {@code GET /api/v1/games/{gameId}/me}: only the caller's own state and, while the caller is in an
 * unreleased match, that match's number, court and state; {@code null} otherwise (blueprint section 13). No id, name or
 * band.
 */
public record MyAttendanceResponse(AttendanceState attendanceState, MyAssignment assignment) {}
