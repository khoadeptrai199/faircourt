package com.faircourt.player.member;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal routes called only by playmatch-service over the private Compose network (AD-15; My attendance design
 * section 3): {@code POST /internal/v1/players/members} finds or creates the member's profile, {@code GET
 * /internal/v1/players/by-user/{userId}} finds it.
 */
@RestController
public class MemberProfileController {

    private final MemberProfileService service;

    public MemberProfileController(MemberProfileService service) {
        this.service = service;
    }

    @PostMapping(value = "/internal/v1/players/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemberProfileNamedResponse> findOrCreate(@Valid @RequestBody MemberProfileRequest request) {
        MemberProfileService.Profile profile = service.findOrCreate(request.userId(), request.displayName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MemberProfileNamedResponse(profile.id(), profile.displayName()));
    }

    @GetMapping("/internal/v1/players/by-user/{userId}")
    public ResponseEntity<MemberProfileResponse> byUser(@PathVariable String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        UUID playerId = service.findByUser(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MemberProfileResponse(playerId));
    }
}
