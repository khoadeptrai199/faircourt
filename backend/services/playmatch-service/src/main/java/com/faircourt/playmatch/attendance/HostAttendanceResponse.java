package com.faircourt.playmatch.attendance;

import java.time.Instant;
import java.util.UUID;

/** Answer of the host's attendance change: host audience only, three fields and no personal data. */
public record HostAttendanceResponse(UUID attendanceId, AttendanceState state, Instant availableSince) {}
