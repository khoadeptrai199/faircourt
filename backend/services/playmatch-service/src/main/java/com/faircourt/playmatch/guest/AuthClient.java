package com.faircourt.playmatch.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * playmatch-service's call to auth-service: {@code POST /internal/v1/auth/guest-credentials} (AD-15). Short
 * timeouts, no retry of a write. The answer holds the raw guest token: it is passed on to the caller and never logged.
 */
@Component
public class AuthClient {

    private final RestClient client;

    AuthClient(ServiceClients clients, @Value("${faircourt.services.auth-url}") String authUrl) {
        this.client = clients.create(authUrl);
    }

    private record IssueRequest(UUID gameId, UUID attendanceId, Instant expiresAt) {}

    /** Any failure (auth-service down, slow or refusing) is a 503: the caller then compensates. */
    public GuestCredential issue(UUID gameId, UUID attendanceId, Instant expiresAt) {
        try {
            GuestCredential credential = client.post()
                    .uri("/internal/v1/auth/guest-credentials")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new IssueRequest(gameId, attendanceId, expiresAt))
                    .retrieve()
                    .body(GuestCredential.class);
            if (credential == null || credential.token() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return credential;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
