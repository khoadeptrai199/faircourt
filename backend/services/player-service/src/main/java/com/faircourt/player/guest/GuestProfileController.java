package com.faircourt.player.guest;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /internal/v1/players/guests}: internal route, called only by playmatch-service over the private Compose
 * network (AD-15; Guest check-in design section 3). Creates the guest profile and the self-reported assessment.
 */
@RestController
public class GuestProfileController {

    private final GuestProfileService service;

    public GuestProfileController(GuestProfileService service) {
        this.service = service;
    }

    @PostMapping(value = "/internal/v1/players/guests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuestProfileResponse> create(@Valid @RequestBody CreateGuestProfileRequest request) {
        GuestProfileResponse body =
                new GuestProfileResponse(service.create(request.displayName(), request.gameId(), request.answers()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
