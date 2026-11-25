package com.faircourt.player.member;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds or creates the {@code MEMBER} profile of an account (My attendance design section 4). Idempotent: the unique
 * {@code user_id} makes two parallel calls return the same profile. A new profile has band {@code null} and status
 * {@code SELF_REPORTED}. Nothing here logs a name.
 */
@Service
public class MemberProfileService {

    private final JdbcClient jdbc;

    public MemberProfileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The profile's id and its own display name (the given name only when the profile was created now). */
    public record Profile(UUID id, String displayName) {}

    /** @return the profile of this account; created on the first call; 400 for a bad name */
    @Transactional
    public Profile findOrCreate(UUID userId, String displayName) {
        String name = cleanName(displayName);
        // A parallel insert of the same user_id waits for the other transaction, then does nothing; the select below
        // then sees the committed row.
        jdbc.sql("""
						INSERT INTO player_profile (id, user_id, kind, display_name, current_band, band_status)
						VALUES (:id, :userId, 'MEMBER', :name, NULL, 'SELF_REPORTED')
						ON CONFLICT (user_id) DO NOTHING""")
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("name", name)
                .update();
        return jdbc.sql("SELECT id, display_name FROM player_profile WHERE user_id = :userId AND kind = 'MEMBER'")
                .param("userId", userId)
                .query((rs, rowNum) -> new Profile(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .single();
    }

    /** The profile id of this account, or empty when it has none (the seeded host has none until it joins). */
    @Transactional(readOnly = true)
    public Optional<UUID> findByUser(UUID userId) {
        return jdbc.sql("SELECT id FROM player_profile WHERE user_id = :userId AND kind = 'MEMBER'")
                .param("userId", userId)
                .query(UUID.class)
                .optional();
    }

    /**
     * Trimmed, 1-80 characters, no control characters (design D-9). Same rule as GuestProfileService.cleanName: keep
     * them identical.
     */
    static String cleanName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty()
                || name.codePointCount(0, name.length()) > 80
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return name;
    }
}
