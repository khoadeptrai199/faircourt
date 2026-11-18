package com.faircourt.gateway.account;

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
 * The gateway's only call to auth-service: {@code POST /internal/v1/auth/verify} (AD-15). Short timeouts
 * ({@code spring.http.clients}: 2 s connect, 5 s read) and no retry: a failed or slow call is a 503, never a guess.
 * Request bodies and answers are never logged.
 */
@Component
public class AuthClient {

    private final RestClient client;

    public AuthClient(RestClient.Builder builder, @Value("${faircourt.services.auth-url}") String authUrl) {
        this.client = builder.baseUrl(authUrl).build();
    }

    private record SessionCheckResponse(boolean valid) {}

    /** The account the credentials belong to; 401 when they are wrong; 503 when auth-service does not answer. */
    public AccountResponse verify(String email, String password) {
        try {
            return client.post()
                    .uri("/internal/v1/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .body(new LoginRequest(email, password))
                    .retrieve()
                    .body(AccountResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                throw new ApiException(ErrorCode.UNAUTHENTICATED);
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                // The gateway and auth-service disagree about what a valid login is: not "service down".
                throw new ApiException(ErrorCode.VALIDATION_FAILED);
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** Whether a session that logged in at {@code since} is still valid (the password has not changed since). */
    public boolean sessionCheck(UUID userId, Instant since) {
        try {
            SessionCheckResponse answer = client.get()
                    .uri("/internal/v1/auth/accounts/{id}/session-check?since={since}", userId, since.toString())
                    .header(RequestIdFilter.HEADER, RequestIdFilter.currentRequestId())
                    .retrieve()
                    .body(SessionCheckResponse.class);
            return answer != null && answer.valid();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
