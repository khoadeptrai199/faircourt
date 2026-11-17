package com.faircourt.gateway.routing;

import com.faircourt.common.web.ApiError;
import com.faircourt.common.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * A service that does not answer (refused, unreachable, connect or read timeout from
 * {@code spring.http.clients.*-timeout}) → 503 {@code SERVICE_UNAVAILABLE} (AD-15). The gateway's HTTP client reports all
 * of these as {@link ResourceAccessException} (observed in {@code DownstreamErrorIT}); other I/O errors are not mapped here. Error responses that a service
 * does send are passed through unchanged by the gateway, not handled here.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DownstreamErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiError> downstreamUnavailable(ResourceAccessException e) {
        log.warn("Downstream service did not answer: {}", e.getClass().getSimpleName());
        ErrorCode code = ErrorCode.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code.status()).body(ApiError.of(code, code.defaultMessage()));
    }
}
