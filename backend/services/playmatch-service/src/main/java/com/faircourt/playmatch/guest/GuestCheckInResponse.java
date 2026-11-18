package com.faircourt.playmatch.guest;

import com.faircourt.playmatch.attendance.AttendanceState;

/**
 * Answer to the gateway. {@code credential} is set only when a new attendance was created ({@code created = true}).
 * Never reaches the browser as is: the gateway turns it into a cookie and a two-field body.
 */
public record GuestCheckInResponse(AttendanceState attendanceState, boolean created, GuestCredential credential) {}
