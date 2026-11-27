package com.faircourt.playmatch.match;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PATCH /api/v1/host/matches/{matchId}/start|finish|cancel}: the host of the match's game moves it through its
 * life (blueprint section 13; Match start / finish / cancel design 3). Identity comes only from the gateway's identity
 * token; there is no request body.
 */
@RestController
public class HostMatchLifecycleController {

    private final MatchService service;
    private final AccessPolicy policy;

    public HostMatchLifecycleController(MatchService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PatchMapping("/api/v1/host/matches/{matchId}/start")
    public ResponseEntity<HostMatchStateResponse> start(@PathVariable String matchId, CallerIdentity caller) {
        return run(matchId, caller, service::start);
    }

    @PatchMapping("/api/v1/host/matches/{matchId}/finish")
    public ResponseEntity<HostMatchStateResponse> finish(@PathVariable String matchId, CallerIdentity caller) {
        return run(matchId, caller, service::finish);
    }

    @PatchMapping("/api/v1/host/matches/{matchId}/cancel")
    public ResponseEntity<HostMatchStateResponse> cancel(@PathVariable String matchId, CallerIdentity caller) {
        return run(matchId, caller, service::cancel);
    }

    private ResponseEntity<HostMatchStateResponse> run(
            String matchId, CallerIdentity caller, BiFunction<CallerIdentity, UUID, HostMatchStateResponse> command) {
        policy.requireAccount(caller);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(command.apply(caller, parse(matchId)));
    }

    /** A malformed match id is 404, like an unknown match. */
    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
