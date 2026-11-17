package com.faircourt.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void everyCodeHasAnErrorStatusAndASafeMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.status().isError()).as(code.name()).isTrue();
            assertThat(code.defaultMessage()).as(code.name()).isNotBlank();
        }
    }

    @Test
    void serviceUnavailableIs503() {
        assertThat(ErrorCode.SERVICE_UNAVAILABLE.status().value()).isEqualTo(503);
    }

    @Test
    void apiErrorJsonEscapesQuotesAndControlCharacters() {
        ApiError error = new ApiError("STATE_CONFLICT", "say \"hi\"\n\\", "r-1");

        assertThat(error.toJson())
                .isEqualTo("{\"code\":\"STATE_CONFLICT\",\"message\":\"say \\\"hi\\\"\\n\\\\\",\"requestId\":\"r-1\"}");
    }
}
