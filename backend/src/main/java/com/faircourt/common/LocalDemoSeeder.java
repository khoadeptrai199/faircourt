package com.faircourt.common;

import com.faircourt.sessions.PublicCodeGenerator;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local-only, idempotent demo seed (blueprint section 0; owner decision 2026-11-09: 8 READY members).
 *
 * Creates, only if the demo host account does not exist yet:
 * one host account, one OPEN session with 2 courts, and 8 MEMBER players (bands 1,1,2,2,3,3,4,4)
 * each with their own pilot account and a READY attendance. Guests are never seeded.
 * Runs in one transaction, so a failed seed leaves nothing behind.
 */
@Component
@Profile("local")
public class LocalDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    static final int PLAYER_COUNT = 8;
    static final int COURT_COUNT = 2;
    static final String SESSION_TITLE = "FairCourt demo session";
    static final String SESSION_VENUE = "Demo venue (local seed)";
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final PublicCodeGenerator codes;
    private final String hostEmail;
    private final String password;

    public LocalDemoSeeder(
            JdbcClient jdbc,
            TransactionTemplate tx,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${faircourt.seed.host-email}") String hostEmail,
            @Value("${faircourt.seed.password}") String password) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.codes = new PublicCodeGenerator(new SecureRandom());
        this.hostEmail = hostEmail;
        this.password = password;
    }

    public record SeedResult(boolean created, String publicCode) {}

    @Override
    public void run(ApplicationArguments args) {
        SeedResult result = seed();
        log.info(
                "FairCourt local demo seed {} | public code: {} | host login: {}",
                result.created() ? "CREATED" : "already present",
                result.publicCode(),
                hostEmail);
    }

    public SeedResult seed() {
        return tx.execute(status -> {
            Optional<UUID> existingHost = jdbc.sql("SELECT id FROM app_user WHERE lower(email) = lower(:email)")
                    .param("email", hostEmail)
                    .query(UUID.class)
                    .optional();
            if (existingHost.isPresent()) {
                String code = jdbc.sql("""
						SELECT public_code FROM play_session
						WHERE host_user_id = :host ORDER BY created_at, id LIMIT 1""")
                        .param("host", existingHost.get())
                        .query(String.class)
                        .optional()
                        .orElse("(none)");
                return new SeedResult(false, code);
            }
            return new SeedResult(true, insertDemoData());
        });
    }

    private String insertDemoData() {
        String hash = passwordEncoder.encode(password);

        UUID hostId = UUID.randomUUID();
        insertUser(hostId, hostEmail, hash);

        UUID sessionId = UUID.randomUUID();
        String publicCode = codes.next();
        ZonedDateTime start = nextEveningStart();
        jdbc.sql("""
				INSERT INTO play_session (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state)
				VALUES (:id, :code, :host, :title, :venue, :startsAt, :endsAt, :courts, 'OPEN')""")
                .param("id", sessionId)
                .param("code", publicCode)
                .param("host", hostId)
                .param("title", SESSION_TITLE)
                .param("venue", SESSION_VENUE)
                .param("startsAt", start.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
                .param("endsAt", start.plusHours(3).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC))
                .param("courts", COURT_COUNT)
                .update();

        for (int i = 1; i <= PLAYER_COUNT; i++) {
            UUID userId = UUID.randomUUID();
            insertUser(userId, "player" + i + "@faircourt.local", hash);

            UUID profileId = UUID.randomUUID();
            jdbc.sql("""
					INSERT INTO player_profile (id, user_id, kind, display_name, current_band)
					VALUES (:id, :user, 'MEMBER', :name, :band)""")
                    .param("id", profileId)
                    .param("user", userId)
                    .param("name", "Player " + i)
                    .param("band", (i + 1) / 2)
                    .update();

            jdbc.sql("""
					INSERT INTO attendance (id, session_id, player_id, state)
					VALUES (:id, :session, :player, 'READY')""")
                    .param("id", UUID.randomUUID())
                    .param("session", sessionId)
                    .param("player", profileId)
                    .update();
        }
        return publicCode;
    }

    private void insertUser(UUID id, String email, String hash) {
        jdbc.sql("INSERT INTO app_user (id, email, password_hash) VALUES (:id, :email, :hash)")
                .param("id", id)
                .param("email", email)
                .param("hash", hash)
                .update();
    }

    /** Next 18:00 in Asia/Ho_Chi_Minh (after-work start); the session lasts 3 hours. */
    private ZonedDateTime nextEveningStart() {
        ZonedDateTime now = OffsetDateTime.now(clock).atZoneSameInstant(DISPLAY_ZONE);
        ZonedDateTime todayEvening = ZonedDateTime.of(LocalDate.from(now), LocalTime.of(18, 0), DISPLAY_ZONE);
        return now.isBefore(todayEvening) ? todayEvening : todayEvening.plusDays(1);
    }
}
