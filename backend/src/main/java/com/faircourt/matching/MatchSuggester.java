package com.faircourt.matching;

import com.faircourt.matching.BoardSnapshot.ReadyPlayer;
import com.faircourt.matching.SuggestionResult.NoSuggestionReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic v1 match suggestion policy (blueprint sections 5 and 14).
 *
 * Pure function: no repository access, no mutation. Suggestions write nothing; only a host
 * confirmation persists a match. This is intentionally a simple heuristic, not a global optimizer.
 */
public final class MatchSuggester {

    /** Band used for grouping when a player's band is unknown (blueprint section 14). */
    static final int UNKNOWN_BAND_FOR_GROUPING = 2;

    /** "stable UUID order": UUIDs compared by their canonical string form. */
    private static final Comparator<ReadyPlayer> BY_ID =
            Comparator.comparing(p -> p.attendanceId().toString());

    /** Selection priority: fewer games first, then waiting longer, then attendance UUID. */
    private static final Comparator<ReadyPlayer> PRIORITY = Comparator.comparingInt(ReadyPlayer::gamesPlayed)
            .thenComparing(ReadyPlayer::availableSince)
            .thenComparing(BY_ID);

    private static final Comparator<ReadyPlayer> GROUPING =
            Comparator.comparingInt(MatchSuggester::effectiveBand).thenComparing(PRIORITY);

    public SuggestionResult suggest(BoardSnapshot snapshot) {
        List<Integer> courts =
                snapshot.freeCourts().stream().distinct().sorted().toList();
        List<ReadyPlayer> ready = snapshot.readyPlayers();

        if (courts.isEmpty()) {
            return SuggestionResult.none(NoSuggestionReason.NO_FREE_COURT);
        }
        if (ready.size() < 4) {
            return SuggestionResult.none(NoSuggestionReason.WAITING_FOR_PLAYERS);
        }

        int courtsToFill = Math.min(courts.size(), ready.size() / 4);
        int slots = 4 * courtsToFill;

        List<ReadyPlayer> selected =
                ready.stream().sorted(PRIORITY).limit(slots).toList();
        List<ReadyPlayer> grouped = selected.stream().sorted(GROUPING).toList();
        Set<UUID> longestWaiters = longestWaiters(ready);

        List<Proposal> proposals = new ArrayList<>(courtsToFill);
        for (int g = 0; g < courtsToFill; g++) {
            List<ReadyPlayer> four = grouped.subList(4 * g, 4 * g + 4);
            proposals.add(buildProposal(courts.get(g), four, longestWaiters));
        }
        return SuggestionResult.of(proposals);
    }

    private static Proposal buildProposal(int courtNo, List<ReadyPlayer> four, Set<UUID> longestWaiters) {
        Split best = bestSplit(four);

        int min = four.stream().mapToInt(MatchSuggester::effectiveBand).min().orElseThrow();
        int max = four.stream().mapToInt(MatchSuggester::effectiveBand).max().orElseThrow();
        int spread = max - min;

        EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
        if (spread >= 2) {
            warnings.add(Warning.WIDE_SKILL_GAP);
        }
        if (four.stream().anyMatch(p -> p.band() == null)) {
            warnings.add(Warning.UNKNOWN_LEVEL);
        }

        EnumSet<ReasonCode> reasons = EnumSet.noneOf(ReasonCode.class);
        if (spread <= 1) {
            reasons.add(ReasonCode.SIMILAR_LEVEL);
        }
        if (best.teamGap() == 0) {
            reasons.add(ReasonCode.BALANCED_TEAMS);
        }
        if (four.stream().anyMatch(p -> longestWaiters.contains(p.attendanceId()))) {
            reasons.add(ReasonCode.LONG_WAIT);
        }

        return new Proposal(courtNo, ids(best.teamA()), ids(best.teamB()), List.copyOf(reasons), List.copyOf(warnings));
    }

    /**
     * Tests the three unique team splits of four players and ranks them by team band-sum gap, then by
     * recent repeat partners, then stable UUID order (the enumeration order below).
     */
    static Split bestSplit(List<ReadyPlayer> four) {
        List<ReadyPlayer> p = four.stream().sorted(BY_ID).toList();
        List<Split> candidates = List.of(
                Split.of(List.of(p.get(0), p.get(1)), List.of(p.get(2), p.get(3))),
                Split.of(List.of(p.get(0), p.get(2)), List.of(p.get(1), p.get(3))),
                Split.of(List.of(p.get(0), p.get(3)), List.of(p.get(1), p.get(2))));

        Split best = candidates.get(0);
        for (Split candidate : candidates.subList(1, candidates.size())) {
            if (candidate.teamGap() < best.teamGap()
                    || (candidate.teamGap() == best.teamGap() && candidate.repeatPartners() < best.repeatPartners())) {
                best = candidate;
            }
        }
        return best;
    }

    record Split(List<ReadyPlayer> teamA, List<ReadyPlayer> teamB, int teamGap, int repeatPartners) {

        static Split of(List<ReadyPlayer> a, List<ReadyPlayer> b) {
            int gap = Math.abs(bandSum(a) - bandSum(b));
            int repeats = (playedTogether(a.get(0), a.get(1)) ? 1 : 0) + (playedTogether(b.get(0), b.get(1)) ? 1 : 0);
            return new Split(a, b, gap, repeats);
        }
    }

    private static boolean playedTogether(ReadyPlayer x, ReadyPlayer y) {
        return x.recentPartners().contains(y.attendanceId())
                || y.recentPartners().contains(x.attendanceId());
    }

    private static int bandSum(List<ReadyPlayer> team) {
        return team.stream().mapToInt(MatchSuggester::effectiveBand).sum();
    }

    static int effectiveBand(ReadyPlayer p) {
        return p.band() == null ? UNKNOWN_BAND_FOR_GROUPING : p.band();
    }

    /** Players with the earliest availableSince, or empty when everyone became available at the same instant. */
    private static Set<UUID> longestWaiters(List<ReadyPlayer> ready) {
        Instant earliest = ready.stream()
                .map(ReadyPlayer::availableSince)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        boolean allSame = ready.stream().allMatch(p -> p.availableSince().equals(earliest));
        if (allSame) {
            return Set.of();
        }
        return Set.copyOf(ready.stream()
                .filter(p -> p.availableSince().equals(earliest))
                .map(ReadyPlayer::attendanceId)
                .toList());
    }

    private static List<UUID> ids(List<ReadyPlayer> players) {
        return players.stream().map(ReadyPlayer::attendanceId).toList();
    }
}
