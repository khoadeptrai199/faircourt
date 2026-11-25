package com.faircourt.playmatch.attendance;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal routes called only by the gateway (AD-15; My attendance design section 3): {@code POST
 * /internal/v1/games/{gameId}/member-attendances} (member join) and {@code GET
 * /internal/v1/games/{gameId}/my-attendance?userId=|attendanceId=} (exactly one). The gateway resolved who the caller
 * is; the ids here name the caller it resolved, not a claim of the browser.
 */
@RestController
public class MemberAttendanceController {

    private final MemberAttendanceService service;

    public MemberAttendanceController(MemberAttendanceService service) {
        this.service = service;
    }

    @PostMapping(value = "/internal/v1/games/{gameId}/member-attendances", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemberAttendanceResponse> join(
            @PathVariable String gameId, @Valid @RequestBody MemberAttendanceRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.join(gameId(gameId), request.userId(), request.displayName()));
    }

    @GetMapping("/internal/v1/games/{gameId}/my-attendance")
    public ResponseEntity<MyAttendanceResponse> mine(
            @PathVariable String gameId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String attendanceId) {
        UUID game = gameId(gameId);
        if ((userId == null) == (attendanceId == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        UUID user = userId == null ? null : uuidParam(userId);
        UUID attendance = attendanceId == null ? null : uuidParam(attendanceId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.myAttendance(game, user, attendance));
    }

    /** An unknown or malformed game id is 404, like an unknown game. */
    private static UUID gameId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private static UUID uuidParam(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
