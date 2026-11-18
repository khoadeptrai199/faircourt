package com.faircourt.playmatch.guest;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /internal/v1/games/by-code/{publicCode}/guest-check-ins}: internal route, called only by the gateway
 * (AD-15; Guest check-in design section 3). The answer may hold the raw guest token, so it is never cached.
 */
@RestController
public class GuestCheckInController {

    private final GuestCheckInService service;

    public GuestCheckInController(GuestCheckInService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/internal/v1/games/by-code/{publicCode}/guest-check-ins",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuestCheckInResponse> checkIn(
            @PathVariable String publicCode, @Valid @RequestBody GuestCheckInRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.checkIn(publicCode, request));
    }
}
