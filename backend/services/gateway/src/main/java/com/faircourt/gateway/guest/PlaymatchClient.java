package com.faircourt.gateway.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
import com.faircourt.gateway.player.MyAssignment;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The gateway's call to playmatch-service for a guest check-in: {@code POST
 * /internal/v1/games/by-code/{publicCode}/guest-check-ins} (AD-15). Short timeouts and no retry of a write. The browser's
 * cookie is never forwarded: the gateway sends the resolved attendance id as an explicit argument. Bodies and answers
 * are never logged.
 */
@Component
public class PlaymatchClient {

    private final RestClient client;

    public PlaymatchClient(
            RestClient.Builder builder, @Value("${faircourt.services.playmatch-url}") String playmatchUrl) {
        this.client = builder.baseUrl(playmatchUrl).build();
    }

    /** What playmatch-service answers; the credential (raw token) is only set for a new attendance. */
    public record CheckIn(AttendanceState attendanceState, boolean created, Credential credential) {}

    public record Credential(String token, Instant expiresAt) {}

    private record Request(String displayName, GuestCheckInRequest.Answers answers, UUID existingAttendanceId) {}

    /** What playmatch-service answers to a member join: the state now and whether this call created the entry. */
    public record MemberJoin(AttendanceState attendanceState, boolean created) {}

    private record MemberRequest(UUID userId, String displayName) {}

    /** What playmatch-service answers to the own-attendance read: the state and the optional own assignment. */
    public record MyAttendance(AttendanceState attendanceState, MyAssignment assignment) {}

    /**
     * Member join: {@code POST /internal/v1/games/{gameId}/member-attendances}. The account id is the one the gateway
     * resolved from its session. 404 unknown game, 409 closed game or removed player, 503 a service is down. Never
     * retried.
     */
    public MemberJoin joinMember(UUID gameId, UUID userId, String displayName) {
        try {
            MemberJoin answer = client.post()
                    .uri("/internal/v1/games/{gameId}/member-attendances", gameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new MemberRequest(userId, displayName))
                    .retrieve()
                    .body(MemberJoin.class);
            if (answer == null || answer.attendanceState() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return answer;
        } catch (HttpClientErrorException e) {
            throw mapClientError(e);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * The caller's own state: {@code GET /internal/v1/games/{gameId}/my-attendance} with exactly one of {@code userId}
     * (a signed-in account) or {@code attendanceId} (a guest the gateway resolved from the cookie). 404 when it is not
     * this caller's entry in this game.
     */
    public MyAttendance myAttendance(UUID gameId, UUID userId, UUID attendanceId) {
        try {
            MyAttendance answer = (userId != null
                            ? client.get()
                                    .uri("/internal/v1/games/{gameId}/my-attendance?userId={userId}", gameId, userId)
                            : client.get()
                                    .uri(
                                            "/internal/v1/games/{gameId}/my-attendance?attendanceId={attendanceId}",
                                            gameId,
                                            attendanceId))
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .retrieve()
                    .body(MyAttendance.class);
            if (answer == null || answer.attendanceState() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            MyAssignment assignment = answer.assignment();
            if (assignment != null
                    && (assignment.matchNo() < 1
                            || assignment.courtNo() < 1
                            || !("PLANNED".equals(assignment.matchState())
                                    || "ACTIVE".equals(assignment.matchState())))) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return answer;
        } catch (HttpClientErrorException e) {
            throw mapClientError(e);
        } catch (RestClientException e) {
            // Also a body that cannot be read (a broken assignment), not only a failed call: 503, never 500.
            // RestClientException covers RestClientResponseException and ResourceAccessException.
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private static ApiException mapClientError(HttpClientErrorException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == HttpStatus.BAD_REQUEST) {
            return new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (status == HttpStatus.NOT_FOUND) {
            return new ApiException(ErrorCode.NOT_FOUND);
        }
        if (status == HttpStatus.CONFLICT) {
            return new ApiException(ErrorCode.STATE_CONFLICT);
        }
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    public CheckIn checkIn(
            String publicCode, String displayName, GuestCheckInRequest.Answers answers, UUID existingAttendanceId) {
        try {
            CheckIn answer = client.post()
                    .uri("/internal/v1/games/by-code/{code}/guest-check-ins", publicCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new Request(displayName, answers, existingAttendanceId))
                    .retrieve()
                    .body(CheckIn.class);
            if (answer == null
                    || answer.attendanceState() == null
                    || (answer.created() && answer.credential() == null)) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return answer;
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.BAD_REQUEST) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            if (status == HttpStatus.NOT_FOUND) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            if (status == HttpStatus.CONFLICT) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "This game is closed.");
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
