package com.faircourt.playmatch.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Create game TC-15: the cross-field rules of {@link GameService}, with a fixed clock and no Spring (OD-26). */
class GameServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.ofHours(7));
    }

    private static CreateGameRequest request(Instant start, Instant end, CreateGameRequest.Price price) {
        return new CreateGameRequest("Title", "Venue", at(start), at(end), 2, MatchFormat.DOUBLES, 6, price);
    }

    private static CreateGameRequest times(Instant start, Instant end) {
        return request(start, end, null);
    }

    private static void assertInvalid(CreateGameRequest request) {
        assertThatThrownBy(() -> GameService.validateRules(request, NOW))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static CreateGameRequest.Price male(Integer min, Integer max) {
        return new CreateGameRequest.Price(new CreateGameRequest.Line(min, max), null);
    }

    @Test
    void endAfterStartAndNotYetEndedAndShortEnoughIsAccepted() {
        Instant start = NOW.plus(Duration.ofHours(1));

        assertThatCode(() -> GameService.validateRules(times(start, start.plus(Duration.ofHours(3))), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void endEqualToOrBeforeStartIsRejected() {
        Instant start = NOW.plus(Duration.ofHours(1));

        assertInvalid(times(start, start));
        assertInvalid(times(start, start.minus(Duration.ofMinutes(1))));
    }

    @Test
    void aGameThatHasEndedIsRejectedButOneEndingAfterNowIsAccepted() {
        assertInvalid(times(NOW.minus(Duration.ofHours(3)), NOW));
        assertThatCode(() -> GameService.validateRules(
                        times(NOW.minus(Duration.ofHours(3)), NOW.plus(Duration.ofSeconds(1))), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void twelveHoursIsAcceptedAndOneMinuteMoreIsRejected() {
        Instant start = NOW.plus(Duration.ofHours(1));

        assertThatCode(() -> GameService.validateRules(times(start, start.plus(Duration.ofHours(12))), NOW))
                .doesNotThrowAnyException();
        assertInvalid(times(start, start.plus(Duration.ofHours(12)).plus(Duration.ofMinutes(1))));
    }

    @Test
    void minAboveMaxIsRejectedAndOneBoundAloneIsAccepted() {
        Instant start = NOW.plus(Duration.ofHours(1));
        Instant end = start.plus(Duration.ofHours(2));

        assertInvalid(request(start, end, male(90000, 80000)));
        assertThatCode(() -> GameService.validateRules(request(start, end, male(80000, null)), NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> GameService.validateRules(request(start, end, male(null, 80000)), NOW))
                .doesNotThrowAnyException();
        assertThatCode(() -> GameService.validateRules(request(start, end, male(80000, 80000)), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void oneBoundAloneIsUsedAsBothBounds() {
        assertThat(PriceRange.of(80000, null)).isEqualTo(new PriceRange(80000, 80000));
        assertThat(PriceRange.of(null, 80000)).isEqualTo(new PriceRange(80000, 80000));
    }
}
