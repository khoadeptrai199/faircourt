package com.faircourt.playmatch.match;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.matching.BoardSnapshot;
import com.faircourt.matching.BoardSnapshot.ReadyPlayer;
import com.faircourt.matching.MatchFormat;
import com.faircourt.matching.MatchSuggester;
import com.faircourt.matching.SuggestionResult;
import com.faircourt.playmatch.access.AccessPolicy;
import com.faircourt.playmatch.game.GameState;
import com.faircourt.playmatch.player.PlayerLevelsClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Match suggestions (design 3, 4, 5): policy, then one read-only snapshot of the game, then one call to player-service
 * for the private levels (outside any transaction), then the pure {@link MatchSuggester}. Writes nothing.
 */
@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private final AccessPolicy policy;
    private final SuggestionSnapshotQuery query;
    private final PlayerLevelsClient levels;
    private final MatchSuggester suggester = new MatchSuggester();

    public SuggestionService(AccessPolicy policy, SuggestionSnapshotQuery query, PlayerLevelsClient levels) {
        this.policy = policy;
        this.query = query;
        this.levels = levels;
    }

    public HostSuggestionResponse suggest(CallerIdentity caller, UUID gameId) {
        policy.requireHost(caller, gameId);
        SuggestionSnapshotQuery.Snapshot snapshot =
                query.read(gameId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (snapshot.game().state() == GameState.CLOSED) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }

        Map<UUID, PlayerLevelsClient.Level> byPlayer = new HashMap<>();
        if (!snapshot.pool().isEmpty()) {
            List<UUID> playerIds = snapshot.pool().stream()
                    .map(SuggestionSnapshotQuery.PoolPlayer::playerId)
                    .distinct()
                    .toList();
            for (PlayerLevelsClient.Level level : levels.levels(playerIds)) {
                byPlayer.put(level.playerId(), level);
            }
        }

        List<ReadyPlayer> ready = new ArrayList<>();
        for (SuggestionSnapshotQuery.PoolPlayer player : snapshot.pool()) {
            PlayerLevelsClient.Level level = byPlayer.get(player.playerId());
            if (level == null) {
                // Treated as unknown level; log the attendance id only (no name).
                log.warn("No level from player-service for attendance {}", player.attendanceId());
            }
            ready.add(new ReadyPlayer(
                    player.attendanceId(),
                    level == null ? null : level.band(),
                    player.availableSince(),
                    player.matchesPlayed(),
                    player.recentPartners()));
        }

        Set<Integer> busy = Set.copyOf(snapshot.busyCourts());
        List<Integer> freeCourts = new ArrayList<>();
        for (int courtNo = 1; courtNo <= snapshot.game().courtCount(); courtNo++) {
            if (!busy.contains(courtNo)) {
                freeCourts.add(courtNo);
            }
        }

        MatchFormat format = switch (snapshot.game().format()) {
            case SINGLES -> MatchFormat.SINGLES;
            case DOUBLES -> MatchFormat.DOUBLES;
        };
        SuggestionResult result = suggester.suggest(new BoardSnapshot(ready, freeCourts, format));
        return new HostSuggestionResponse(
                result.proposals().stream()
                        .map(p -> new HostSuggestionResponse.Suggestion(
                                p.courtNo(),
                                new HostSuggestionResponse.Teams(p.teamA(), p.teamB()),
                                p.reasonCodes(),
                                p.warnings()))
                        .toList(),
                result.noSuggestionReason());
    }
}
