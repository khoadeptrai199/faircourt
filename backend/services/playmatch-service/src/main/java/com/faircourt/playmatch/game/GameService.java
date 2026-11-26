package com.faircourt.playmatch.game;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import com.faircourt.playmatch.access.AccessPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts a new game: the caller becomes its only host (blueprint section 11; Create game design 4). One local
 * transaction, no call to another service. The public code is inserted with {@code ON CONFLICT DO NOTHING} so a
 * collision does not abort the PostgreSQL transaction and the next code can be tried.
 */
@Service
public class GameService {

    static final int TITLE_MAX = 120;
    static final int VENUE_MAX = 160;
    static final Duration MAX_LENGTH = Duration.ofHours(12);
    static final int CODE_TRIES = 5;

    private final JdbcClient jdbc;
    private final AccessPolicy policy;
    private final PublicCodeGenerator codes;
    private final Clock clock;

    public GameService(JdbcClient jdbc, AccessPolicy policy, PublicCodeGenerator codes, Clock clock) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional
    public CreateGameResponse create(CallerIdentity caller, CreateGameRequest request) {
        CallerIdentity.Account account = policy.requireAccount(caller);
        Instant now = clock.instant();
        validateRules(request, now);

        CreateGameRequest.Price price = request.pricePerPerson();
        PriceRange male = line(price == null ? null : price.male());
        PriceRange female = line(price == null ? null : price.female());
        UUID id = UUID.randomUUID();
        for (int attempt = 0; attempt < CODE_TRIES; attempt++) {
            String code = codes.next();
            int inserted = jdbc.sql("""
                            INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at,
                                              court_count, match_format, min_sets,
                                              price_male_min_vnd, price_male_max_vnd,
                                              price_female_min_vnd, price_female_max_vnd, created_at)
                            VALUES (:id, :code, :host, :title, :venue, :startsAt, :endsAt,
                                    :courtCount, :matchFormat, :minSets,
                                    :maleMin, :maleMax, :femaleMin, :femaleMax, :createdAt)
                            ON CONFLICT (public_code) DO NOTHING""")
                    .param("id", id)
                    .param("code", code)
                    .param("host", account.userId())
                    .param("title", request.title().trim())
                    .param("venue", request.venue().trim())
                    .param("startsAt", request.startsAt().withOffsetSameInstant(ZoneOffset.UTC))
                    .param("endsAt", request.endsAt().withOffsetSameInstant(ZoneOffset.UTC))
                    .param("courtCount", request.courtCount())
                    .param("matchFormat", request.matchFormat().name())
                    .param("minSets", request.minSets())
                    .param("maleMin", male == null ? null : male.minVnd())
                    .param("maleMax", male == null ? null : male.maxVnd())
                    .param("femaleMin", female == null ? null : female.minVnd())
                    .param("femaleMax", female == null ? null : female.maxVnd())
                    .param("createdAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                    .update();
            if (inserted == 1) {
                return new CreateGameResponse(id, code);
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    /** Rules that Bean Validation cannot express (design 3.1, OD-26); every failure is 400. */
    static void validateRules(CreateGameRequest request, Instant now) {
        if (request.title().trim().length() > TITLE_MAX
                || request.venue().trim().length() > VENUE_MAX) {
            throw invalid();
        }
        Instant startsAt = request.startsAt().toInstant();
        Instant endsAt = request.endsAt().toInstant();
        if (!endsAt.isAfter(startsAt)
                || !endsAt.isAfter(now)
                || Duration.between(startsAt, endsAt).compareTo(MAX_LENGTH) > 0) {
            throw invalid();
        }
        CreateGameRequest.Price price = request.pricePerPerson();
        if (price != null) {
            checkLine(price.male());
            checkLine(price.female());
        }
    }

    private static void checkLine(CreateGameRequest.Line line) {
        if (line != null && line.minVnd() != null && line.maxVnd() != null && line.minVnd() > line.maxVnd()) {
            throw invalid();
        }
    }

    private static PriceRange line(CreateGameRequest.Line line) {
        return line == null ? null : PriceRange.of(line.minVnd(), line.maxVnd());
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED);
    }
}
