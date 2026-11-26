package com.faircourt.player.level;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal route called only by playmatch-service over the private Compose network (AD-15; Host board design 3.3):
 * {@code POST /internal/v1/players/levels}. POST only because a game can have many players; it writes nothing.
 */
@RestController
public class PlayerLevelsController {

    private final PlayerLevelsQuery query;

    public PlayerLevelsController(PlayerLevelsQuery query) {
        this.query = query;
    }

    @PostMapping(value = "/internal/v1/players/levels", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PlayerLevelsResponse> levels(@Valid @RequestBody PlayerLevelsRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PlayerLevelsResponse(query.find(request.playerIds())));
    }
}
