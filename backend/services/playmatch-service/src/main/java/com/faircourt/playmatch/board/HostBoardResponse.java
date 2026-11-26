package com.faircourt.playmatch.board;

import com.faircourt.playmatch.attendance.AttendanceState;
import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.game.MatchFormat;
import com.faircourt.playmatch.match.MatchState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The whole board of one game for its host (blueprint sections 3, 13; Host board design 3.1). Host audience only:
 * never reused for a player or the public. No user id, player id, email, token or questionnaire answers.
 */
public record HostBoardResponse(Instant asOf, Game game, List<Player> players, List<Court> courts) {

    public record Game(
            UUID id,
            String publicCode,
            String title,
            String venue,
            Instant startsAt,
            Instant endsAt,
            int courtCount,
            GameState state,
            MatchFormat matchFormat,
            int minSets) {}

    /** {@code kind}, {@code band} and {@code bandStatus} are null when player-service did not know the player. */
    public record Player(
            UUID attendanceId,
            String displayName,
            String kind,
            AttendanceState state,
            Integer band,
            String bandStatus,
            Instant checkedInAt,
            Instant availableSince,
            int matchesPlayed) {}

    /** {@code match} is the one PLANNED or ACTIVE match on this court, else null. */
    public record Court(int courtNo, Match match) {}

    public record Match(UUID matchId, int matchNo, MatchState state, Teams teams) {}

    /** Attendance ids of the unreleased players, per team. */
    public record Teams(List<UUID> A, List<UUID> B) {}
}
