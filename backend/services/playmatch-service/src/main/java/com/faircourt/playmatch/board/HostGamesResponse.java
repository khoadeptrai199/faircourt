package com.faircourt.playmatch.board;

import com.faircourt.playmatch.game.GameState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The games the caller hosts (Host board design 3.2). Host audience only; no names or players. */
public record HostGamesResponse(List<Game> games) {

    public record Game(
            UUID id,
            String publicCode,
            String title,
            String venue,
            Instant startsAt,
            Instant endsAt,
            int courtCount,
            GameState state) {}
}
