package com.faircourt.matching;

import java.util.List;
import java.util.UUID;

/** One suggested court: two doubles teams plus rule-based explanations (blueprint sections 13 and 14). */
public record Proposal(
        int courtNo, List<UUID> teamA, List<UUID> teamB, List<ReasonCode> reasonCodes, List<Warning> warnings) {

    public Proposal {
        teamA = List.copyOf(teamA);
        teamB = List.copyOf(teamB);
        reasonCodes = List.copyOf(reasonCodes);
        warnings = List.copyOf(warnings);
    }
}
