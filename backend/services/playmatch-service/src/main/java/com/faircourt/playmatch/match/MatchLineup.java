package com.faircourt.playmatch.match;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.game.MatchFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The attendance ids of one match, per team, checked against the game's format: doubles 2 + 2, singles 1 + 1, every id
 * distinct (blueprint section 12, OD-20). A wrong shape is a 400.
 */
public record MatchLineup(List<UUID> teamA, List<UUID> teamB) {

    public static MatchLineup of(List<UUID> teamA, List<UUID> teamB, MatchFormat format) {
        int perTeam = format == MatchFormat.SINGLES ? 1 : 2;
        if (teamA == null || teamB == null || teamA.size() != perTeam || teamB.size() != perTeam) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        if (all.stream().anyMatch(id -> id == null) || new HashSet<>(all).size() != all.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return new MatchLineup(List.copyOf(teamA), List.copyOf(teamB));
    }

    /** Every id, sorted: the order in which rows are locked, so two commands cannot deadlock. */
    public List<UUID> all() {
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        all.sort(null);
        return all;
    }
}
