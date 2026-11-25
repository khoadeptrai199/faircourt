package com.faircourt.playmatch.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
import java.util.Optional;
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
 * playmatch-service's call to player-service: {@code POST /internal/v1/players/guests} (AD-15). Short timeouts
 * ({@code faircourt.services}: 2 s connect, 5 s read) and no retry of a write. Names and answers are never logged.
 */
@Component
public class PlayerClient {

    private final RestClient client;

    PlayerClient(ServiceClients clients, @Value("${faircourt.services.player-url}") String playerUrl) {
        this.client = clients.create(playerUrl);
    }

    public record Answers(Integer serve, Integer rally, Integer movement, Integer positioning) {}

    private record CreateRequest(String displayName, UUID gameId, Answers answers) {}

    private record CreateResponse(UUID playerId) {}

    private record MemberRequest(UUID userId, String displayName) {}

    /** A member's profile id and the profile's own display name. */
    public record MemberProfile(UUID playerId, String displayName) {}

    /**
     * The profile of a signed-in member, created by player-service when the account has none ({@code POST
     * /internal/v1/players/members}); the name is the profile's own, the given one only for a new profile. 503 when
     * player-service does not answer; the write is not retried.
     */
    public MemberProfile findOrCreateMember(UUID userId, String displayName) {
        try {
            MemberProfile response = client.post()
                    .uri("/internal/v1/players/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new MemberRequest(userId, displayName))
                    .retrieve()
                    .body(MemberProfile.class);
            if (response == null || response.playerId() == null || response.displayName() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return response;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** The profile id of a member ({@code GET /internal/v1/players/by-user/{userId}}); empty when there is none. */
    public Optional<UUID> findMember(UUID userId) {
        try {
            CreateResponse response = client.get()
                    .uri("/internal/v1/players/by-user/{userId}", userId)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .retrieve()
                    .body(CreateResponse.class);
            if (response == null || response.playerId() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return Optional.of(response.playerId());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** The new guest profile's id; 400 when player-service refuses the answers; 503 when it does not answer. */
    public UUID createGuest(String displayName, UUID gameId, Answers answers) {
        try {
            CreateResponse response = client.post()
                    .uri("/internal/v1/players/guests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new CreateRequest(displayName, gameId, answers))
                    .retrieve()
                    .body(CreateResponse.class);
            if (response == null || response.playerId() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return response.playerId();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
