package com.faircourt.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.matching.BoardSnapshot.ReadyPlayer;
import com.faircourt.matching.SuggestionResult.NoSuggestionReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Policy and invariant tests for blueprint section 14 boundary cases. */
class MatchSuggesterTest {

    private static final Instant T0 = Instant.parse("2026-11-09T11:00:00Z");

    private final MatchSuggester suggester = new MatchSuggester();

    // ---- helpers -------------------------------------------------------------------------

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    private static ReadyPlayer player(int n, Integer band) {
        return new ReadyPlayer(id(n), band, T0, 0, Set.of());
    }

    private static ReadyPlayer player(int n, Integer band, int gamesPlayed, Instant availableSince) {
        return new ReadyPlayer(id(n), band, availableSince, gamesPlayed, Set.of());
    }

    private static BoardSnapshot snapshot(List<ReadyPlayer> ready, Integer... freeCourts) {
        return new BoardSnapshot(ready, List.of(freeCourts));
    }

    private static Set<UUID> allIds(Proposal p) {
        Set<UUID> ids = new HashSet<>(p.teamA());
        ids.addAll(p.teamB());
        return ids;
    }

    // ---- section 14 boundary tests -------------------------------------------------------

    @Test
    void fourEqualBandPlayersFormOneBalancedSimilarCourt() {
        var ready = List.of(player(1, 2), player(2, 2), player(3, 2), player(4, 2));

        SuggestionResult result = suggester.suggest(snapshot(ready, 1));

        assertThat(result.noSuggestionReason()).isNull();
        assertThat(result.proposals()).hasSize(1);
        Proposal p = result.proposals().getFirst();
        assertThat(p.courtNo()).isEqualTo(1);
        assertThat(p.teamA()).hasSize(2);
        assertThat(p.teamB()).hasSize(2);
        assertThat(allIds(p)).containsExactlyInAnyOrder(id(1), id(2), id(3), id(4));
        assertThat(p.reasonCodes()).contains(ReasonCode.SIMILAR_LEVEL, ReasonCode.BALANCED_TEAMS);
        assertThat(p.warnings()).isEmpty();
    }

    @Test
    void bands1234WarnWideSkillGapEvenWhenTeamSumsTie() {
        var ready = List.of(player(1, 1), player(2, 2), player(3, 3), player(4, 4));

        Proposal p = suggester.suggest(snapshot(ready, 1)).proposals().getFirst();

        assertThat(p.warnings()).contains(Warning.WIDE_SKILL_GAP);
        assertThat(p.reasonCodes()).contains(ReasonCode.BALANCED_TEAMS).doesNotContain(ReasonCode.SIMILAR_LEVEL);
        // 1+4 vs 2+3 is the only zero-gap split.
        assertThat(Set.copyOf(p.teamA())).isIn(Set.of(id(1), id(4)), Set.of(id(2), id(3)));
    }

    @Test
    void unknownBandIsFlaggedAndGroupedAsBand2() {
        // Two courts: unknown player (band null -> 2 for grouping) must land with the 1s/2s, not the 4s.
        var ready = List.of(
                player(1, 1),
                player(2, 1),
                player(3, 2),
                player(4, null),
                player(5, 4),
                player(6, 4),
                player(7, 4),
                player(8, 4));

        SuggestionResult result = suggester.suggest(snapshot(ready, 1, 2));

        Proposal lowCourt = result.proposals().get(0);
        assertThat(allIds(lowCourt)).containsExactlyInAnyOrder(id(1), id(2), id(3), id(4));
        assertThat(lowCourt.warnings()).contains(Warning.UNKNOWN_LEVEL);
        assertThat(result.proposals().get(1).warnings()).doesNotContain(Warning.UNKNOWN_LEVEL);
    }

    @Test
    void zeroGamePlayersAreSelectedAheadOfOneGamePlayers() {
        // One-game players became available earlier, but games played is the first criterion.
        Instant earlier = T0.minusSeconds(3600);
        var ready = List.of(
                player(1, 2, 1, earlier),
                player(2, 2, 1, earlier),
                player(3, 2, 1, earlier),
                player(4, 2, 1, earlier),
                player(5, 2, 0, T0),
                player(6, 2, 0, T0),
                player(7, 2, 0, T0),
                player(8, 2, 0, T0));

        Proposal p = suggester.suggest(snapshot(ready, 1)).proposals().getFirst();

        assertThat(allIds(p)).containsExactlyInAnyOrder(id(5), id(6), id(7), id(8));
    }

    @Test
    void amongEqualGamesTheLongerWaitingPlayersAreSelected() {
        var ready = List.of(
                player(1, 2, 0, T0), player(2, 2, 0, T0),
                player(3, 2, 0, T0.minusSeconds(600)), player(4, 2, 0, T0.minusSeconds(600)),
                player(5, 2, 0, T0.minusSeconds(900)), player(6, 2, 0, T0.minusSeconds(900)));

        Proposal p = suggester.suggest(snapshot(ready, 1)).proposals().getFirst();

        assertThat(allIds(p)).containsExactlyInAnyOrder(id(3), id(4), id(5), id(6));
        assertThat(p.reasonCodes()).contains(ReasonCode.LONG_WAIT);
    }

    @Test
    void fewerThanFourReadyGivesWaitingForPlayers() {
        var ready = List.of(player(1, 2), player(2, 2), player(3, 2));

        SuggestionResult result = suggester.suggest(snapshot(ready, 1, 2));

        assertThat(result.proposals()).isEmpty();
        assertThat(result.noSuggestionReason()).isEqualTo(NoSuggestionReason.WAITING_FOR_PLAYERS);
    }

    @Test
    void noFreeCourtGivesNoFreeCourt() {
        var ready = List.of(player(1, 2), player(2, 2), player(3, 2), player(4, 2));

        SuggestionResult result = suggester.suggest(snapshot(ready));

        assertThat(result.proposals()).isEmpty();
        assertThat(result.noSuggestionReason()).isEqualTo(NoSuggestionReason.NO_FREE_COURT);
    }

    @Test
    void occupiedCourtIsExcluded() {
        // Court 1 is busy, only court 2 is free.
        var ready =
                Stream.iterate(1, i -> i + 1).limit(8).map(i -> player(i, 2)).toList();

        SuggestionResult result = suggester.suggest(snapshot(ready, 2));

        assertThat(result.proposals()).hasSize(1);
        assertThat(result.proposals().getFirst().courtNo()).isEqualTo(2);
    }

    @Test
    void simultaneousCourtsNeverReuseAnAttendanceId() {
        var ready = new ArrayList<ReadyPlayer>();
        for (int i = 1; i <= 13; i++) {
            ready.add(player(i, (i % 4) + 1));
        }

        SuggestionResult result = suggester.suggest(snapshot(ready, 1, 2, 3, 4));

        // 13 ready -> floor(13/4) = 3 courts.
        assertThat(result.proposals()).hasSize(3);
        List<UUID> used =
                result.proposals().stream().flatMap(p -> allIds(p).stream()).toList();
        assertThat(used).hasSize(12).doesNotHaveDuplicates();
        assertThat(result.proposals()).extracting(Proposal::courtNo).containsExactly(1, 2, 3);
    }

    @Test
    void sameSnapshotGivesSameResultRegardlessOfInputOrder() {
        var ready = new ArrayList<ReadyPlayer>();
        for (int i = 1; i <= 10; i++) {
            ready.add(player(i, (i % 4) + 1, i % 2, T0.minusSeconds(i * 60L)));
        }
        SuggestionResult first = suggester.suggest(snapshot(ready, 1, 2));

        var shuffled = new ArrayList<>(ready);
        Collections.shuffle(shuffled, new Random(42));
        SuggestionResult second = suggester.suggest(snapshot(shuffled, 2, 1));

        assertThat(second).isEqualTo(first);
        assertThat(suggester.suggest(snapshot(ready, 1, 2))).isEqualTo(first);
    }

    // ---- section 5 / 14 ranking rules ----------------------------------------------------

    @Test
    void recentRepeatPartnersArePenalizedWhenTeamGapTies() {
        // All band 2: every split has gap 0. Players 1 & 2 partnered recently, so they should be split up.
        var ready = List.of(
                new ReadyPlayer(id(1), 2, T0, 0, Set.of(id(2))),
                new ReadyPlayer(id(2), 2, T0, 0, Set.of(id(1))),
                player(3, 2),
                player(4, 2));

        Proposal p = suggester.suggest(snapshot(ready, 1)).proposals().getFirst();

        boolean together =
                p.teamA().containsAll(List.of(id(1), id(2))) || p.teamB().containsAll(List.of(id(1), id(2)));
        assertThat(together).isFalse();
    }

    @Test
    void teamGapIsRankedBeforeRepeatPartners() {
        // Bands 1,1,3,3. Gap-0 splits are {1,3|2,4} and {1,4|2,3}; player 1 recently partnered BOTH 3 and 4,
        // so every gap-0 split repeats a partner. The only repeat-free split {1,2|3,4} has gap 4.
        // Team gap must win: a gap-0 split is chosen despite the repeat.
        var ready = List.of(
                new ReadyPlayer(id(1), 1, T0, 0, Set.of(id(3), id(4))), player(2, 1), player(3, 3), player(4, 3));

        Proposal p = suggester.suggest(snapshot(ready, 1)).proposals().getFirst();

        assertThat(p.reasonCodes()).contains(ReasonCode.BALANCED_TEAMS);
        boolean bandOnePlayersTogether =
                p.teamA().containsAll(List.of(id(1), id(2))) || p.teamB().containsAll(List.of(id(1), id(2)));
        assertThat(bandOnePlayersTogether).isFalse();
    }

    @Test
    void seededDemoGroupFormsTwoSimilarBalancedCourts() {
        // Seed (owner decision): 8 READY members, bands 1,1,2,2,3,3,4,4, 2 courts.
        var ready = List.of(
                player(1, 1),
                player(2, 1),
                player(3, 2),
                player(4, 2),
                player(5, 3),
                player(6, 3),
                player(7, 4),
                player(8, 4));

        SuggestionResult result = suggester.suggest(snapshot(ready, 1, 2));

        assertThat(result.proposals()).hasSize(2);
        Proposal court1 = result.proposals().get(0);
        Proposal court2 = result.proposals().get(1);
        assertThat(allIds(court1)).containsExactlyInAnyOrder(id(1), id(2), id(3), id(4));
        assertThat(allIds(court2)).containsExactlyInAnyOrder(id(5), id(6), id(7), id(8));
        for (Proposal p : result.proposals()) {
            assertThat(p.reasonCodes()).contains(ReasonCode.SIMILAR_LEVEL, ReasonCode.BALANCED_TEAMS);
            assertThat(p.warnings()).isEmpty();
        }
    }
}
