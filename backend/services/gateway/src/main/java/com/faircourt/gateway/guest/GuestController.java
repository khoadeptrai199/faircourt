package com.faircourt.gateway.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.gateway.account.AuthClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/public/games/{publicCode}/guests}: a visitor checks in as a guest (blueprint sections 12, 13;
 * Guest check-in design). Answered by the gateway because it alone touches cookies: it resolves a guest cookie that
 * the browser already holds, asks playmatch-service to check in (passing the resolved attendance id as an explicit
 * argument), and sets the cookie from the credential playmatch-service returns.
 *
 * <p>Security rules (OWASP Session Management and CSRF cheat sheets): CSRF token required by the filter chain; JSON
 * only; body capped at 4 KB before it is parsed; the raw token goes only into the cookie; the answer has two fields
 * and is {@code no-store}; a cookie of another game, or an expired, revoked or unknown one, is ignored; nothing
 * personal or secret is logged.
 */
@RestController
public class GuestController {

    private static final Pattern CODE = Pattern.compile("[A-Z0-9]{1,20}");
    /** Blueprint section 13 text for a pending guest. */
    static final String PENDING_MESSAGE = "Waiting for host approval";

    private final AuthClient authClient;
    private final PlaymatchClient playmatchClient;
    private final GuestCookie guestCookie;

    public GuestController(AuthClient authClient, PlaymatchClient playmatchClient, GuestCookie guestCookie) {
        this.authClient = authClient;
        this.playmatchClient = playmatchClient;
        this.guestCookie = guestCookie;
    }

    @PostMapping(value = "/api/v1/public/games/{publicCode}/guests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GuestCheckInResponse> checkIn(
            @PathVariable String publicCode, @RequestBody GuestCheckInRequest body, HttpServletRequest request) {
        String name = validName(body);
        validateAnswers(body);
        String code = publicCode.strip().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // A cookie the browser already holds: who is it? Unknown, expired or revoked means "no cookie".
        Optional<UUID> existing = guestCookie.valueIn(request).flatMap(authClient::resolveGuest);

        PlaymatchClient.CheckIn result = playmatchClient.checkIn(code, name, body.answers(), existing.orElse(null));

        GuestCheckInResponse response = new GuestCheckInResponse(
                result.attendanceState(), result.attendanceState() == AttendanceState.PENDING ? PENDING_MESSAGE : null);
        ResponseEntity.BodyBuilder answer = ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore());
        if (result.created()) {
            answer.header(
                    HttpHeaders.SET_COOKIE,
                    guestCookie
                            .issue(
                                    result.credential().token(),
                                    result.credential().expiresAt())
                            .toString());
        }
        return answer.body(response);
    }

    /**
     * Trimmed, 1-80 characters, no control characters (design D-9). Same rule as GuestCheckInService.cleanName in
     * playmatch-service and GuestProfileService.cleanName in player-service: keep the three identical.
     */
    private static String validName(GuestCheckInRequest body) {
        String name = body == null || body.displayName() == null
                ? ""
                : body.displayName().strip();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return name;
    }

    /**
     * All four answers 0-3, or all four {@code null}, or no {@code answers} at all (blueprint section 13). Same rule
     * as GuestAnswers.parse in player-service: keep them identical.
     */
    private static void validateAnswers(GuestCheckInRequest body) {
        GuestCheckInRequest.Answers a = body.answers();
        if (a == null) {
            return;
        }
        Integer[] values = {a.serve(), a.rally(), a.movement(), a.positioning()};
        int unknown = 0;
        for (Integer value : values) {
            if (value == null) {
                unknown++;
            } else if (value < 0 || value > 3) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
        }
        if (unknown != 0 && unknown != values.length) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
