package com.faircourt.playmatch.seed;

import com.faircourt.playmatch.game.PublicCodeGenerator;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local-only, idempotent demo seed for the playmatch schema (blueprint section 0; owner decision 2026-11-09:
 * 8 READY members; F18 design section 4).
 *
 * Creates, only if the demo game does not exist yet: one OPEN doubles game with 2 courts, minimum 6 sets,
 * price per person men 75,000-85,000 VND / women 65,000-75,000 VND (owner 2026-11-17), and 8 READY attendance
 * rows. IDs are derived exactly as in auth-service and player-service ({@link #demoId}), so the host and player
 * references line up across schemas without shared code. Runs in one transaction.
 */
@Component
@Profile("local")
public class LocalDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    static final int PLAYER_COUNT = 8;
    static final int COURT_COUNT = 2;
    static final int MIN_SETS = 6;
    static final String GAME_TITLE = "FairCourt demo game";
    static final String GAME_VENUE = "Demo venue (local seed)";
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // The key "session:1" predates the rename to "game" (OD-22). It is kept on purpose: the id must stay the same so a
    // local database seeded before the rename is recognised and not seeded a second time.
    static final UUID GAME_ID = demoId("session:1");
    static final UUID HOST_USER_ID = demoId("user:host");

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PublicCodeGenerator codes;

    public LocalDemoSeeder(JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.codes = new PublicCodeGenerator(new SecureRandom());
    }

    public record SeedResult(boolean created, String publicCode) {}

    /** Same derivation in every service: {@code UUID.nameUUIDFromBytes("faircourt-demo:<kind>:<n>")}. */
    static UUID demoId(String key) {
        return UUID.nameUUIDFromBytes(("faircourt-demo:" + key).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(ApplicationArguments args) {
        SeedResult result = seed();
        log.info(
                "FairCourt local demo seed (playmatch) {} | public code: {}",
                result.created() ? "CREATED" : "already present",
                result.publicCode());
    }

    public SeedResult seed() {
        return tx.execute(status -> {
            Optional<String> existing = jdbc.sql("SELECT public_code FROM game WHERE id = :id")
                    .param("id", GAME_ID)
                    .query(String.class)
                    .optional();
            if (existing.isPresent()) {
                return new SeedResult(false, existing.get());
            }
            return new SeedResult(true, insertDemoData());
        });
    }

    private String insertDemoData() {
        String publicCode = codes.next();
        ZonedDateTime start = nextEveningStart();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count,
				  state, match_format, min_sets,
				  price_male_min_vnd, price_male_max_vnd, price_female_min_vnd, price_female_max_vnd)
				VALUES (:id, :code, :host, :title, :venue, :startsAt, :endsAt, :courts,
				  'OPEN', 'DOUBLES', :minSets,
				  75000, 85000, 65000, 75000)""")
                .param("id", GAME_ID)
                .param("code", publicCode)
                .param("host", HOST_USER_ID)
                .param("title", GAME_TITLE)
                .param("venue", GAME_VENUE)
                .param("startsAt", start.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
                .param("endsAt", start.plusHours(3).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
                .param("courts", COURT_COUNT)
                .param("minSets", MIN_SETS)
                .update();

        for (int i = 1; i <= PLAYER_COUNT; i++) {
            jdbc.sql("""
					INSERT INTO attendance (id, game_id, player_id, display_name, state)
					VALUES (:id, :game, :player, :name, 'READY')""")
                    .param("id", demoId("attendance:" + i))
                    .param("game", GAME_ID)
                    .param("player", demoId("player:" + i))
                    .param("name", "Player " + i)
                    .update();
        }
        return publicCode;
    }

    /** Next 18:00 in Asia/Ho_Chi_Minh (after-work start); the game lasts 3 hours. */
    private ZonedDateTime nextEveningStart() {
        ZonedDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(DISPLAY_ZONE);
        ZonedDateTime todayEvening = ZonedDateTime.of(LocalDate.from(now), LocalTime.of(18, 0), DISPLAY_ZONE);
        return now.isBefore(todayEvening) ? todayEvening : todayEvening.plusDays(1);
    }
}
