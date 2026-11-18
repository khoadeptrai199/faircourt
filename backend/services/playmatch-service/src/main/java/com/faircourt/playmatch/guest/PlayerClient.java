package com.faircourt.playmatch.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
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
