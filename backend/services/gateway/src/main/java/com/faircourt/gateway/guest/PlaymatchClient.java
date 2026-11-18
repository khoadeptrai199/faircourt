package com.faircourt.gateway.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
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
