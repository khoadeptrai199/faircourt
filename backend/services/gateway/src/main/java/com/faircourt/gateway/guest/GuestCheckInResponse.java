package com.faircourt.gateway.guest;

/**
 * Answer to a guest check-in: only the attendance state and a message (design section 3). No id, name or band; the
 * guest credential travels only in the HttpOnly cookie.
 */
public record GuestCheckInResponse(AttendanceState attendanceState, String message) {}
