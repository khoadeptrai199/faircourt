package com.faircourt.player.level;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read projection of the level of many players (Host board design 3.3): selects only the four allowed columns. */
@Component
public class PlayerLevelsQuery {

    private final JdbcClient jdbc;

    public PlayerLevelsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlayerLevelsResponse.Entry> find(List<UUID> playerIds) {
        return jdbc.sql("""
						SELECT id, kind, current_band, band_status
						FROM player_profile
						WHERE id = ANY(:ids)""")
                .param("ids", playerIds.toArray(new UUID[0]))
                .query((rs, rowNum) -> new PlayerLevelsResponse.Entry(
                        rs.getObject("id", UUID.class),
                        rs.getString("kind"),
                        rs.getObject("current_band", Integer.class),
                        rs.getString("band_status")))
                .list();
    }
}
