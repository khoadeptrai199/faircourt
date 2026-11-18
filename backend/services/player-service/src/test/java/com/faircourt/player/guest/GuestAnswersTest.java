package com.faircourt.player.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Guest questionnaire mapping (blueprint section 13): totals to bands, "all four scored or all four unknown". */
class GuestAnswersTest {

    private static int bandOf(int serve, int rally, int movement, int positioning) {
        return new GuestAnswers(serve, rally, movement, positioning).band().value();
    }

    @Test
    void totalsOnTheEdgesOfEachBandMapToTheBlueprintBands() {
        assertThat(bandOf(0, 0, 0, 0)).isEqualTo(1);
        assertThat(bandOf(1, 1, 1, 0)).isEqualTo(1);
        assertThat(bandOf(1, 1, 1, 1)).isEqualTo(2);
        assertThat(bandOf(2, 2, 2, 0)).isEqualTo(2);
        assertThat(bandOf(2, 2, 2, 1)).isEqualTo(3);
        assertThat(bandOf(3, 3, 3, 0)).isEqualTo(3);
        assertThat(bandOf(3, 3, 3, 1)).isEqualTo(4);
        assertThat(bandOf(3, 3, 3, 3)).isEqualTo(4);
    }

    @Test
    void allFourUnknownMeansNoAnswersAndNoBand() {
        assertThat(GuestAnswers.parse(null, null, null, null)).isEqualTo(Optional.empty());
    }

    @Test
    void fourScoredAnswersAreParsed() {
        assertThat(GuestAnswers.parse(2, 1, 1, 1)).contains(new GuestAnswers(2, 1, 1, 1));
    }

    @Test
    void aPartialSetIsRefused() {
        assertThatThrownBy(() -> GuestAnswers.parse(1, 1, 1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuestAnswers.parse(null, 1, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aValueOutsideZeroToThreeIsRefused() {
        assertThatThrownBy(() -> GuestAnswers.parse(4, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuestAnswers.parse(0, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStoredJsonHoldsOnlyTheFourNumbers() {
        assertThat(new GuestAnswers(2, 1, 0, 3).toJson())
                .isEqualTo("{\"serve\":2,\"rally\":1,\"movement\":0,\"positioning\":3}");
    }
}
