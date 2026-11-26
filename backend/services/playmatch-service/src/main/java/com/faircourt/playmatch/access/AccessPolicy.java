package com.faircourt.playmatch.access;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Service-level host check (blueprint sections 11, 15): the host of a game is the account in {@code
 * game.host_user_id}, never a role flag. Order of answers: no account 401, unknown game 404, other account 403.
 */
@Component
public class AccessPolicy {

    private final JdbcClient jdbc;

    public AccessPolicy(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The signed-in account, or 401 for any other caller. */
    public CallerIdentity.Account requireAccount(CallerIdentity caller) {
        if (caller instanceof CallerIdentity.Account account) {
            return account;
        }
        throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }

    /** Passes only for the host of this game; 401 / 404 / 403 otherwise. */
    public CallerIdentity.Account requireHost(CallerIdentity caller, UUID gameId) {
        CallerIdentity.Account account = requireAccount(caller);
        Optional<UUID> host = jdbc.sql("SELECT host_user_id FROM game WHERE id = :id")
                .param("id", gameId)
                .query(UUID.class)
                .optional();
        UUID owner = host.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!owner.equals(account.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return account;
    }
}
