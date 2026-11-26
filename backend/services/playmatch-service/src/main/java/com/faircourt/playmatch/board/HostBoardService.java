package com.faircourt.playmatch.board;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import com.faircourt.playmatch.player.PlayerLevelsClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Host board read (Host board design 3.1, 5): policy, then the playmatch reads in one read-only transaction, then one
 * call to player-service outside any transaction. A failing player-service is 503; there is no partial board.
 */
@Service
public class HostBoardService {

    private static final Logger log = LoggerFactory.getLogger(HostBoardService.class);

    private final AccessPolicy policy;
    private final HostBoardQuery query;
    private final PlayerLevelsClient levels;
    private final Clock clock;

    public HostBoardService(AccessPolicy policy, HostBoardQuery query, PlayerLevelsClient levels, Clock clock) {
        this.policy = policy;
        this.query = query;
        this.levels = levels;
        this.clock = clock;
    }

    public HostBoardResponse board(CallerIdentity caller, UUID gameId) {
        policy.requireHost(caller, gameId);
        HostBoardQuery.Snapshot snapshot = query.read(gameId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        List<UUID> playerIds = snapshot.attendance().stream()
                .map(HostBoardQuery.AttendanceRow::playerId)
                .distinct()
                .toList();
        Map<UUID, PlayerLevelsClient.Level> byPlayer = new HashMap<>();
        if (!playerIds.isEmpty()) {
            for (PlayerLevelsClient.Level level : levels.levels(playerIds)) {
                byPlayer.put(level.playerId(), level);
            }
        }

        List<HostBoardResponse.Player> players = new ArrayList<>();
        for (HostBoardQuery.AttendanceRow row : snapshot.attendance()) {
            PlayerLevelsClient.Level level = byPlayer.get(row.playerId());
            if (level == null) {
                // Should not happen; log the attendance id only (no name).
                log.warn("No level from player-service for attendance {}", row.attendanceId());
            }
            players.add(new HostBoardResponse.Player(
                    row.attendanceId(),
                    row.displayName(),
                    level == null ? null : level.kind(),
                    row.state(),
                    level == null ? null : level.band(),
                    level == null ? null : level.bandStatus(),
                    row.checkedInAt(),
                    row.availableSince(),
                    row.matchesPlayed()));
        }

        List<HostBoardResponse.Court> courts = new ArrayList<>();
        for (int courtNo = 1; courtNo <= snapshot.game().courtCount(); courtNo++) {
            HostBoardResponse.Match match = null;
            for (HostBoardQuery.CurrentMatch current : snapshot.matches()) {
                if (current.courtNo() == courtNo) {
                    match = new HostBoardResponse.Match(
                            current.matchId(),
                            current.matchNo(),
                            current.state(),
                            new HostBoardResponse.Teams(current.teamA(), current.teamB()));
                    break;
                }
            }
            courts.add(new HostBoardResponse.Court(courtNo, match));
        }
        return new HostBoardResponse(clock.instant(), snapshot.game(), players, courts);
    }
}
