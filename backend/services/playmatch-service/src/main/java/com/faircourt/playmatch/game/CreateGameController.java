package com.faircourt.playmatch.game;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.playmatch.access.AccessPolicy;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/games}: any signed-in account posts a game and becomes its only host (blueprint sections 11, 13;
 * Create game design 3.1). Identity comes only from the gateway's identity token; the body has no host field.
 */
@RestController
public class CreateGameController {

    private final GameService service;
    private final AccessPolicy policy;

    public CreateGameController(GameService service, AccessPolicy policy) {
        this.service = service;
        this.policy = policy;
    }

    @PostMapping(value = "/api/v1/games", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateGameResponse> create(
            CallerIdentity caller, @Valid @RequestBody CreateGameRequest request) {
        // Spring resolves every argument first, so a malformed body is 400 even without a token (same as the host
        // attendance route). Within this method the account check (401) runs before anything is written.
        policy.requireAccount(caller);
        CreateGameResponse created = service.create(caller, request);
        return ResponseEntity.created(URI.create("/api/v1/host/games/" + created.gameId() + "/board"))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }
}
