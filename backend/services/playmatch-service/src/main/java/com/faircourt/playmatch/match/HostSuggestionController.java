package com.faircourt.playmatch.match;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/host/games/{gameId}/suggestions}: this game's host only (blueprint section 13). Read only: no
 * body is read and nothing is saved. Identity comes only from the gateway's identity token.
 */
@RestController
public class HostSuggestionController {

    private final SuggestionService service;
    private final AccessPolicy policy;

    public HostSuggestionController(SuggestionService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping("/api/v1/host/games/{gameId}/suggestions")
    public ResponseEntity<HostSuggestionResponse> suggest(@PathVariable String gameId, CallerIdentity caller) {
        // Identity is checked before the id is looked at, so an anonymous caller learns nothing about game ids.
        policy.requireAccount(caller);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.suggest(caller, parse(gameId)));
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
