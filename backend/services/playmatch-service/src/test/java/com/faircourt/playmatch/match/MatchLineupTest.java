package com.faircourt.playmatch.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.game.MatchFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchLineupTest {

    private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private final UUID d = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    private void assertInvalid(List<UUID> teamA, List<UUID> teamB, MatchFormat format) {
        assertThatThrownBy(() -> MatchLineup.of(teamA, teamB, format))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void aDoublesLineupOfTwoPlusTwoDistinctIdsIsAcceptedAndAllIsSorted() {
        MatchLineup lineup = MatchLineup.of(List.of(d, a), List.of(c, b), MatchFormat.DOUBLES);

        assertThat(lineup.teamA()).containsExactly(d, a);
        assertThat(lineup.teamB()).containsExactly(c, b);
        assertThat(lineup.all()).containsExactly(a, b, c, d);
    }

    @Test
    void aDoublesLineupWithDuplicatesOrWrongSizesIsRejected() {
        assertInvalid(List.of(a, a), List.of(c, d), MatchFormat.DOUBLES);
        assertInvalid(List.of(a, b), List.of(a, d), MatchFormat.DOUBLES);
        assertInvalid(List.of(a), List.of(c, d), MatchFormat.DOUBLES);
        assertInvalid(List.of(a, b, c), List.of(d), MatchFormat.DOUBLES);
        assertInvalid(List.of(a), List.of(b), MatchFormat.DOUBLES);
        assertInvalid(null, List.of(c, d), MatchFormat.DOUBLES);
        List<UUID> withNull = new ArrayList<>();
        withNull.add(a);
        withNull.add(null);
        assertInvalid(withNull, List.of(c, d), MatchFormat.DOUBLES);
    }

    @Test
    void aSinglesLineupNeedsOnePlayerPerTeamAndTwoDistinctIds() {
        MatchLineup lineup = MatchLineup.of(List.of(a), List.of(b), MatchFormat.SINGLES);

        assertThat(lineup.all()).containsExactly(a, b);
        assertInvalid(List.of(a), List.of(a), MatchFormat.SINGLES);
        assertInvalid(List.of(a, b), List.of(c, d), MatchFormat.SINGLES);
    }
}
