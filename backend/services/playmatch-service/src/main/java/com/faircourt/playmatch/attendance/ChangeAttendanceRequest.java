package com.faircourt.playmatch.attendance;

import jakarta.validation.constraints.NotNull;

/**
 * Body of the host's attendance change (blueprint section 13). Only {@code READY} and {@code LEFT} are accepted; the
 * service answers 400 for any other state.
 */
public record ChangeAttendanceRequest(@NotNull AttendanceState state) {}
