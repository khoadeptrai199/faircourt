package com.faircourt.playmatch.match;

import com.faircourt.matching.ReasonCode;
import com.faircourt.matching.SuggestionResult.NoSuggestionReason;
import com.faircourt.matching.Warning;
import java.util.List;
import java.util.UUID;

/**
 * Suggested courts for the host (blueprint section 13, Match suggestions design 3.1). Host audience only; attendance
 * ids only (no names, bands or player ids). Nothing here is saved.
 */
public record HostSuggestionResponse(List<Suggestion> suggestions, NoSuggestionReason noSuggestionReason) {

    public record Suggestion(int courtNo, Teams teams, List<ReasonCode> reasonCodes, List<Warning> warnings) {}

    /** Attendance ids per team: 2 each in doubles, 1 each in singles. */
    public record Teams(List<UUID> A, List<UUID> B) {}
}
