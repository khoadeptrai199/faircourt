package com.faircourt.playmatch.match;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/host/games/{gameId}/matches}: this game's host confirms one proposed or edited lineup as a
 * {@code PLANNED} match (blueprint section 13). Identity comes only from the gateway's identity token.
 */
@RestController
public class HostMatchController {

    private final MatchService service;
    private final AccessPolicy policy;

    public HostMatchController(MatchService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping(value = "/api/v1/host/games/{gameId}/matches", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfirmMatchResponse> confirm(
            @PathVariable String gameId, CallerIdentity caller, @RequestBody(required = false) String body) {
        // Order of answers (design 3.1): 401 here, then 404 (game id), 403 and 400 (body) inside the command.
        policy.requireAccount(caller);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.confirm(caller, parse(gameId), body));
    }

    /** A malformed game id is 404, like an unknown game. */
    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }
}
