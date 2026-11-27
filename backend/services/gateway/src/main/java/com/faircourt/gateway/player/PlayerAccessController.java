package com.faircourt.gateway.player;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.gateway.account.AccountResponse;
import com.faircourt.gateway.account.AuthClient;
import com.faircourt.gateway.guest.GuestCookie;
import com.faircourt.gateway.guest.PlaymatchClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Member join and "my game" (blueprint sections 3, 11, 13; My attendance design sections 3, 4, 8). Answered by the
 * gateway because only it can read the account session and the guest cookie. Identity comes only from the account
 * session or the guest cookie; the {@code gameId} in the path names the game, never the caller. The browser's cookie is
 * never forwarded: playmatch-service gets the resolved ids as explicit arguments. Answers are {@code no-store}; no
 * name, email or token is logged.
 */
@RestController
public class PlayerAccessController {

    private final AuthClient authClient;
    private final PlaymatchClient playmatchClient;
    private final GuestCookie guestCookie;

    public PlayerAccessController(AuthClient authClient, PlaymatchClient playmatchClient, GuestCookie guestCookie) {
        this.authClient = authClient;
        this.playmatchClient = playmatchClient;
        this.guestCookie = guestCookie;
    }

    /** {@code POST /api/v1/games/{gameId}/join}: a signed-in account joins as a member; CSRF required by the filter. */
    @PostMapping("/api/v1/games/{gameId}/join")
    public ResponseEntity<MemberJoinResponse> join(@PathVariable String gameId) {
        AccountResponse account = account().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        UUID game = parseGameId(gameId);

        PlaymatchClient.MemberJoin result = playmatchClient.joinMember(game, account.userId(), memberName(account));

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(new MemberJoinResponse(result.attendanceState()));
    }

    /**
     * {@code GET /api/v1/games/{gameId}/me}: the caller's own state. An account session wins over a guest cookie. A
     * guest cookie that was sent but does not resolve (unknown, expired or revoked) is {@code 401
     * GUEST_ACCESS_EXPIRED}; no credential at all is {@code 401 UNAUTHENTICATED}.
     */
    @GetMapping("/api/v1/games/{gameId}/me")
    public ResponseEntity<MyAttendanceResponse> me(@PathVariable String gameId, HttpServletRequest request) {
        Optional<AccountResponse> account = account();
        UUID userId = null;
        UUID attendanceId = null;
        if (account.isPresent()) {
            userId = account.get().userId();
        } else {
            String cookie = guestCookie.valueIn(request).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
            attendanceId =
                    authClient.resolveGuest(cookie).orElseThrow(() -> new ApiException(ErrorCode.GUEST_ACCESS_EXPIRED));
        }
        // After the identity check, so a caller without credentials learns nothing about game ids.
        UUID game = parseGameId(gameId);

        PlaymatchClient.MyAttendance own = playmatchClient.myAttendance(game, userId, attendanceId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MyAttendanceResponse(own.attendanceState(), own.assignment()));
    }

    /** The signed-in account of this request, if any (the gateway's own session; never a header or a body field). */
    private static Optional<AccountResponse> account() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountResponse account) {
            return Optional.of(account);
        }
        return Optional.empty();
    }

    /** A malformed game id is 404, like an unknown game. */
    private static UUID parseGameId(String gameId) {
        try {
            return UUID.fromString(gameId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * Display name of a member (design D-1): the part of the account email before the {@code @}, at most 80
     * code points. Control characters are removed so playmatch-service never refuses a valid account. Used only when the
     * member profile is created now; an existing profile keeps its own name. An empty result is refused downstream (400).
     */
    static String memberName(AccountResponse account) {
        String email = account.email() == null ? "" : account.email();
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        // Cut by code points (not UTF-16 units) so a surrogate pair is never split.
        String clean = local.codePoints()
                .filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        if (clean.codePointCount(0, clean.length()) > 80) {
            clean = clean.substring(0, clean.offsetByCodePoints(0, 80)).strip();
        }
        return clean;
    }
}
