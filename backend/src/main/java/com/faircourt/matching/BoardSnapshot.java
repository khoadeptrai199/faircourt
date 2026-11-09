package com.faircourt.matching;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only input for {@link MatchSuggester} (blueprint section 14).
 *
 * @param readyPlayers checked-in READY players who are not in another unfinished match
 * @param freeCourts   court numbers with no PLANNED/ACTIVE match
 */
public record BoardSnapshot(List<ReadyPlayer> readyPlayers, List<Integer> freeCourts) {

    public BoardSnapshot {
        readyPlayers = List.copyOf(readyPlayers);
        freeCourts = List.copyOf(freeCourts);
    }

    /**
     * @param attendanceId   attendance row id
     * @param band           private band 1..4, or null when unknown
     * @param availableSince when this player last became available
     * @param gamesPlayed    finished games this session
     * @param recentPartners partners from this player's last three finished matches
     */
    public record ReadyPlayer(
            UUID attendanceId, Integer band, Instant availableSince, int gamesPlayed, Set<UUID> recentPartners) {

        public ReadyPlayer {
            Objects.requireNonNull(attendanceId, "attendanceId");
            Objects.requireNonNull(availableSince, "availableSince");
            if (band != null && (band < 1 || band > 4)) {
                throw new IllegalArgumentException("band must be 1..4 or null");
            }
            recentPartners = recentPartners == null ? Set.of() : Set.copyOf(recentPartners);
        }
    }
}
