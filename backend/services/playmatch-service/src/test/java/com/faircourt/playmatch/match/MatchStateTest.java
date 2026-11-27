package com.faircourt.playmatch.match;

import static com.faircourt.playmatch.match.MatchState.ACTIVE;
import static com.faircourt.playmatch.match.MatchState.PLANNED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchStateTest {

    @Test
    void onlyAPlannedMatchCanStart() {
        for (MatchState state : MatchState.values()) {
            assertThat(state.canStart()).as(state.name()).isEqualTo(state == PLANNED);
        }
    }

    @Test
    void onlyAnActiveMatchCanFinish() {
        for (MatchState state : MatchState.values()) {
            assertThat(state.canFinish()).as(state.name()).isEqualTo(state == ACTIVE);
        }
    }

    @Test
    void aPlannedOrActiveMatchCanBeCancelled() {
        for (MatchState state : MatchState.values()) {
            assertThat(state.canCancel()).as(state.name()).isEqualTo(state == PLANNED || state == ACTIVE);
        }
    }

    @Test
    void finishedAndCancelledMatchesAllowNothing() {
        for (MatchState state : new MatchState[] {MatchState.FINISHED, MatchState.CANCELLED}) {
            assertThat(state.canStart()).as(state.name()).isFalse();
            assertThat(state.canFinish()).as(state.name()).isFalse();
            assertThat(state.canCancel()).as(state.name()).isFalse();
        }
    }
}
