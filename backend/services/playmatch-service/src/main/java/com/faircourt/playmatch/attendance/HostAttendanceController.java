package com.faircourt.playmatch.attendance;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PATCH /api/v1/host/games/{gameId}/attendance/{attendanceId}}: this game's host approves a pending guest or
 * marks a player as left (blueprint section 13; Attendance management design 3.1). Identity comes only from the
 * gateway's identity token; the path ids name the target, never the caller.
 */
@RestController
public class HostAttendanceController {

    private final AttendanceService service;
    private final AccessPolicy policy;

    public HostAttendanceController(AttendanceService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PatchMapping(
            value = "/api/v1/host/games/{gameId}/attendance/{attendanceId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HostAttendanceResponse> change(
            @PathVariable String gameId,
            @PathVariable String attendanceId,
            CallerIdentity caller,
            @Valid @RequestBody ChangeAttendanceRequest request) {
        // Spring resolves every argument first, so a malformed body is 400 even without a token (observed, see
        // HostAttendanceIT). Within this method the account check (401) runs before the ids are looked at.
        policy.requireAccount(caller);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.changeState(caller, parse(gameId), parse(attendanceId), request.state()));
    }

    /** A malformed id is 404, like an unknown one. */
    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
