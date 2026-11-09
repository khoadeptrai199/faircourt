package com.faircourt.matching;

import java.util.List;

/**
 * Output of {@link MatchSuggester}. Either one or more proposals, or none with a reason
 * (blueprint section 14: WAITING_FOR_PLAYERS or NO_FREE_COURT).
 */
public record SuggestionResult(List<Proposal> proposals, NoSuggestionReason noSuggestionReason) {

    public enum NoSuggestionReason {
        WAITING_FOR_PLAYERS,
        NO_FREE_COURT
    }

    public SuggestionResult {
        proposals = List.copyOf(proposals);
    }

    static SuggestionResult none(NoSuggestionReason reason) {
        return new SuggestionResult(List.of(), reason);
    }

    static SuggestionResult of(List<Proposal> proposals) {
        return new SuggestionResult(proposals, null);
    }
}
