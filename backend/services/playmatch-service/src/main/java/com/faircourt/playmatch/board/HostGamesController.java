package com.faircourt.playmatch.board;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.playmatch.access.AccessPolicy;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/host/games}: the games the signed-in account hosts (Host board design 3.2, OD-04). The list is
 * filtered by the caller's own id, so no further check is needed.
 */
@RestController
public class HostGamesController {

    private final HostGamesQuery query;
    private final AccessPolicy policy;

    public HostGamesController(HostGamesQuery query, AccessPolicy policy) {
        this.query = query;
        this.policy = policy;
    }

    @GetMapping("/api/v1/host/games")
    public ResponseEntity<HostGamesResponse> mine(CallerIdentity caller) {
        CallerIdentity.Account account = policy.requireAccount(caller);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new HostGamesResponse(query.byHost(account.userId())));
    }
}
