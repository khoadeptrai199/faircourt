package com.faircourt.playmatch.guest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Body of the internal route {@code POST /internal/v1/games/by-code/{publicCode}/guest-check-ins} (the gateway is the
 * only caller). {@code existingAttendanceId} is the attendance the gateway resolved from a valid guest cookie, or
 * {@code null}; it is never a claim of the browser.
 */
public record GuestCheckInRequest(
        @NotBlank @Size(max = 200) String displayName, PlayerClient.Answers answers, UUID existingAttendanceId) {}
