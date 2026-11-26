package com.faircourt.playmatch.board;

import com.faircourt.playmatch.game.GameState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read projection: the newest 50 games of one host, covered by {@code ix_game_host_start}. */
@Component
public class HostGamesQuery {

    static final int LIMIT = 50;

    private final JdbcClient jdbc;

    public HostGamesQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<HostGamesResponse.Game> byHost(UUID hostUserId) {
        return jdbc.sql("""
						SELECT id, public_code, title, venue, starts_at, ends_at, court_count, state
						FROM game
						WHERE host_user_id = :host
						ORDER BY starts_at DESC, id
						LIMIT :limit""")
                .param("host", hostUserId)
                .param("limit", LIMIT)
                .query((rs, rowNum) -> new HostGamesResponse.Game(
                        rs.getObject("id", UUID.class),
                        rs.getString("public_code"),
                        rs.getString("title"),
                        rs.getString("venue"),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("court_count"),
                        GameState.valueOf(rs.getString("state"))))
                .list();
    }
}
