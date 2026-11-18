package com.faircourt.auth.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal guest-credential routes (AD-15; Guest check-in design section 3). Never routed by the gateway's public
 * table. Answers are {@code no-store}; nothing is logged.
 *
 * <ul>
 *   <li>{@code POST /internal/v1/auth/guest-credentials}: playmatch-service issues a credential for an attendance.
 *   <li>{@code POST /internal/v1/auth/guest-credentials/resolve}: the gateway asks who a cookie belongs to.
 * </ul>
 */
@RestController
public class GuestCredentialController {

    public record IssueRequest(
            @NotNull UUID gameId,
            @NotNull UUID attendanceId,
            @NotNull Instant expiresAt) {}

    public record IssueResponse(String token, Instant expiresAt) {}

    public record ResolveRequest(@NotBlank @Size(max = 128) String token) {}

    public record ResolveResponse(UUID gameId, UUID attendanceId) {}

    private final GuestCredentialService service;

    public GuestCredentialController(GuestCredentialService service) {
        this.service = service;
    }

    @PostMapping(value = "/internal/v1/auth/guest-credentials", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        GuestCredentialService.Issued issued =
                service.issue(request.gameId(), request.attendanceId(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(new IssueResponse(issued.token(), issued.expiresAt()));
    }

    /** 200 for a valid token; 401 with the same body for unknown, expired and revoked ones. */
    @PostMapping(value = "/internal/v1/auth/guest-credentials/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResolveResponse> resolve(@Valid @RequestBody ResolveRequest request) {
        GuestCredentialService.Resolved resolved =
                service.resolve(request.token()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ResolveResponse(resolved.gameId(), resolved.attendanceId()));
    }
}
