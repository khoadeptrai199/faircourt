package com.faircourt.playmatch.board;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/host/games/{gameId}/board}: this game's host only (blueprint section 13). Identity comes only from
 * the gateway's identity token; the path id names the game, never the caller.
 */
@RestController
public class HostBoardController {

    private final HostBoardService service;
    private final AccessPolicy policy;

    public HostBoardController(HostBoardService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @GetMapping("/api/v1/host/games/{gameId}/board")
    public ResponseEntity<HostBoardResponse> board(@PathVariable String gameId, CallerIdentity caller) {
        // Identity is checked before the id is looked at, so an anonymous caller learns nothing about game ids.
        policy.requireAccount(caller);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.board(caller, parse(gameId)));
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
