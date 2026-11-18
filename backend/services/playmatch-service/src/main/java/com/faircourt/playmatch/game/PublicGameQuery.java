package com.faircourt.playmatch.game;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read projection for the public game page (AD-02): the SQL selects only public columns. */
@Component
public class PublicGameQuery {

    private final JdbcClient jdbc;

    public PublicGameQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PublicGameResponse> findByPublicCode(String publicCode) {
        return jdbc.sql("""
						SELECT id, title, venue, starts_at, ends_at, court_count, state, match_format, min_sets,
						       price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd
						FROM game
						WHERE public_code = :code""")
                .param("code", publicCode)
                .query((rs, rowNum) -> new PublicGameResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("venue"),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("court_count"),
                        GameState.valueOf(rs.getString("state")),
                        MatchFormat.valueOf(rs.getString("match_format")),
                        rs.getInt("min_sets"),
                        PricePerPerson.of(
                                PriceRange.of(
                                        rs.getObject("price_male_min_vnd", Integer.class),
                                        rs.getObject("price_male_max_vnd", Integer.class)),
                                PriceRange.of(
                                        rs.getObject("price_female_min_vnd", Integer.class),
                                        rs.getObject("price_female_max_vnd", Integer.class)))))
                .optional();
    }
}
