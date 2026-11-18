package com.faircourt.player.guest;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the profile of a guest and the self-reported assessment in one transaction (design section 4, step 2). The
 * name is not unique and never merged with another profile (blueprint section 7). Nothing here logs a name or an
 * answer.
 */
@Service
public class GuestProfileService {

    private final JdbcClient jdbc;

    public GuestProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the new profile id; 400 for a bad name or a partial / out-of-range answer set */
    @Transactional
    public UUID create(String displayName, UUID gameId, CreateGuestProfileRequest.RawAnswers raw) {
        String name = cleanName(displayName);
        Optional<GuestAnswers> answers = parse(raw);
        Integer band = answers.map(a -> a.band().value()).orElse(null);
        String answersJson = answers.map(GuestAnswers::toJson).orElse(null);

        UUID playerId = UUID.randomUUID();
        jdbc.sql("""
						INSERT INTO player_profile (id, user_id, kind, display_name, current_band, band_status)
						VALUES (:id, NULL, 'GUEST', :name, :band, 'SELF_REPORTED')""")
                .param("id", playerId)
                .param("name", name)
                .param("band", band)
                .update();
        jdbc.sql("""
						INSERT INTO skill_assessment (id, player_id, game_id, source, answers, band)
						VALUES (:id, :player, :game, 'SELF', CAST(:answers AS jsonb), :band)""")
                .param("id", UUID.randomUUID())
                .param("player", playerId)
                .param("game", gameId)
                .param("answers", answersJson)
                .param("band", band)
                .update();
        return playerId;
    }

    private static Optional<GuestAnswers> parse(CreateGuestProfileRequest.RawAnswers raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return GuestAnswers.parse(raw.serve(), raw.rally(), raw.movement(), raw.positioning());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * Trimmed, 1-80 characters, no control characters (design D-9). Same rule as GuestController.validName in the
     * gateway and GuestCheckInService.cleanName in playmatch-service: keep the three identical.
     */
    static String cleanName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return name;
    }
}
