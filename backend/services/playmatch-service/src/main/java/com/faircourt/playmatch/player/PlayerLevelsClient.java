package com.faircourt.playmatch.player;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
import com.faircourt.playmatch.guest.ServiceClients;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * playmatch-service's call to player-service: {@code POST /internal/v1/players/levels} (AD-15, Host board design
 * 3.3). One batch call (ids in chunks of 200); any failure is 503, so the board is never partial. Read only; not
 * retried here.
 */
@Component
public class PlayerLevelsClient {

    static final int MAX_IDS = 200;

    private final RestClient client;

    PlayerLevelsClient(ServiceClients clients, @Value("${faircourt.services.player-url}") String playerUrl) {
        this.client = clients.create(playerUrl);
    }

    /** Private level of one player; {@code kind} is MEMBER or GUEST, {@code band} 1 to 4 or null. */
    public record Level(UUID playerId, String kind, Integer band, String bandStatus) {}

    private record Request(List<UUID> playerIds) {}

    private record Response(List<Level> players) {}

    /** The levels of the known ids; unknown ids are absent. 503 when player-service does not answer properly. */
    public List<Level> levels(List<UUID> playerIds) {
        List<Level> result = new ArrayList<>();
        for (int from = 0; from < playerIds.size(); from += MAX_IDS) {
            List<UUID> chunk = playerIds.subList(from, Math.min(from + MAX_IDS, playerIds.size()));
            try {
                Response response = client.post()
                        .uri("/internal/v1/players/levels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                        .body(new Request(chunk))
                        .retrieve()
                        .body(Response.class);
                if (response == null || response.players() == null) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
                }
                result.addAll(response.players());
            } catch (RestClientException e) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
        }
        return result;
    }
}
