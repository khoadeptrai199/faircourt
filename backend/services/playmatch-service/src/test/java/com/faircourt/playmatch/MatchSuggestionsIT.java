package com.faircourt.playmatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import com.faircourt.playmatch.match.SuggestionSnapshotQuery;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /api/v1/host/games/{gameId}/suggestions} against PostgreSQL, with player-service replaced by a JDK HTTP
 * stub and the caller given as a test identity token (Match suggestions plan, TC-09 to TC-22). The harness follows
 * {@code HostBoardIT}; no {@code support/TestData} builders exist in this service yet (see implement.md deviations).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.clean-disabled=false", "faircourt.services.read-timeout=400ms"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MatchSuggestionsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = System.getenv("INTERNAL_TOKEN_SECRET");
    private static final Instant T = Instant.parse("2026-11-26T12:30:00Z");
    private static final Instant BASE = Instant.parse("2026-11-26T10:00:00Z");

    /** A clock the test can set; it is the application's clock, so tokens follow it. */
    static final class TestClock extends Clock {
        volatile Instant now = T;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final TestClock CLOCK = new TestClock();

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    /** Stand-in for player-service: answers levels from a map; can fail or stall. */
    private static final class PlayerStub {
        final HttpServer server;
        final Map<UUID, String[]> levels = new ConcurrentHashMap<>();
        final Set<UUID> omit = ConcurrentHashMap.newKeySet();
        final List<String> bodies = new CopyOnWriteArrayList<>();
        volatile int failStatus;
        volatile long stallMillis;

        PlayerStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            levels.clear();
            omit.clear();
            bodies.clear();
            failStatus = 0;
            stallMillis = 0;
        }

        private void handle(HttpExchange exchange) throws IOException {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(request);
            try {
                if (stallMillis > 0) {
                    Thread.sleep(stallMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int status = 200;
            StringBuilder body = new StringBuilder("{\"players\":[");
            if (failStatus != 0) {
                status = failStatus;
                body = new StringBuilder("{}");
            } else {
                boolean first = true;
                for (JsonNode id : JSON.readTree(request).get("playerIds")) {
                    UUID playerId = UUID.fromString(id.asString());
                    String[] level = levels.get(playerId);
                    if (level == null || omit.contains(playerId)) {
                        continue;
                    }
                    body.append(first ? "" : ",")
                            .append("{\"playerId\":\"")
                            .append(playerId)
                            .append("\",\"kind\":\"MEMBER\",\"band\":")
                            .append(level[0])
                            .append(",\"bandStatus\":\"CONFIRMED\"}");
                    first = false;
                }
                body.append("]}");
            }
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static final PlayerStub PLAYER = new PlayerStub();

    @DynamicPropertySource
    static void playerServiceIsTheStub(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.player-url", PLAYER::url);
    }

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SuggestionSnapshotQuery snapshotQuery;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID host = UUID.randomUUID();

    @BeforeEach
    void reset() {
        TestDatabase.reset(jdbc, flyway);
        PLAYER.reset();
        CLOCK.now = T;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static String suggestionsPath(String gameId) {
        return "/api/v1/host/games/" + gameId + "/suggestions";
    }

    private static String tokenFor(UUID user, String gameId) {
        return new IdentityToken(SECRET, CLOCK).issue(user, "playmatch-service", "POST", suggestionsPath(gameId));
    }

    private HttpResponse<String> suggest(String gameId, String token) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + suggestionsPath(gameId)));
        if (token != null) {
            request.header(IdentityTokenHeader.NAME, token);
        }
        return http.send(
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> hostSuggest(UUID game) throws Exception {
        return suggest(game.toString(), tokenFor(host, game.toString()));
    }

    private UUID insertGame(int courts, String format, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO game (id, public_code, host_user_id, title, venue, starts_at, ends_at, court_count, state,
				                  match_format, min_sets)
				VALUES (:id, :code, :host, 'Friday Badminton', 'Court House D7', :startsAt, :endsAt, :courts, :state,
				        :format, 6)""")
                .param("id", id)
                .param("code", id.toString().substring(0, 8).toUpperCase())
                .param("host", host)
                .param("startsAt", OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC))
                .param("endsAt", OffsetDateTime.ofInstant(BASE.plusSeconds(7200), ZoneOffset.UTC))
                .param("courts", courts)
                .param("state", state)
                .param("format", format)
                .update();
        return id;
    }

    /** A player known to the stub (band as JSON text) and one attendance row of the game. */
    private record Row(UUID attendanceId, UUID playerId) {}

    private Row attend(UUID game, String state, String band, long availableMinutes, int matchesPlayed) {
        UUID player = UUID.randomUUID();
        PLAYER.levels.put(player, new String[] {band});
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO attendance (id, game_id, player_id, display_name, state, checked_in_at, available_since,
				                        matches_played)
				VALUES (:id, :game, :player, :name, :state, :checkedIn, :available, :played)""")
                .param("id", id)
                .param("game", game)
                .param("player", player)
                .param("name", "Zorbulon " + id.toString().substring(0, 4))
                .param("state", state)
                .param("checkedIn", OffsetDateTime.ofInstant(BASE.minusSeconds(60), ZoneOffset.UTC))
                .param("available", OffsetDateTime.ofInstant(BASE.plusSeconds(availableMinutes * 60), ZoneOffset.UTC))
                .param("played", matchesPlayed)
                .update();
        return new Row(id, player);
    }

    private Row ready(UUID game, String band) {
        return attend(game, "READY", band, 0, 0);
    }

    private UUID insertMatch(UUID game, int matchNo, int court, String state, Instant finishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
				INSERT INTO play_match (id, game_id, match_no, court_no, state, finished_at)
				VALUES (:id, :game, :no, :court, :state, :finishedAt)""")
                .param("id", id)
                .param("game", game)
                .param("no", matchNo)
                .param("court", court)
                .param("state", state)
                .param("finishedAt", finishedAt == null ? null : OffsetDateTime.ofInstant(finishedAt, ZoneOffset.UTC))
                .update();
        return id;
    }

    private void matchPlayer(UUID match, UUID game, UUID attendance, String team, boolean released) {
        jdbc.sql("""
				INSERT INTO match_player (match_id, game_id, attendance_id, team, released_at)
				VALUES (:match, :game, :attendance, :team, :released)""")
                .param("match", match)
                .param("game", game)
                .param("attendance", attendance)
                .param("team", team)
                .param("released", released ? OffsetDateTime.ofInstant(BASE, ZoneOffset.UTC) : null)
                .update();
    }

    /** Row counts of the four tables plus the sum of {@code next_match_no}: a suggestion must change none of them. */
    private List<Long> counts() {
        List<Long> counts = new ArrayList<>();
        for (String table : new String[] {"game", "attendance", "play_match", "match_player"}) {
            counts.add(
                    jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
        }
        counts.add(jdbc.sql("SELECT COALESCE(sum(next_match_no), 0) FROM game")
                .query(Long.class)
                .single());
        return counts;
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asString());
        }
        return out;
    }

    private static Set<String> codes(JsonNode array) {
        return new HashSet<>(ids(array));
    }

    private static Set<String> suggestedIds(JsonNode body) {
        Set<String> all = new HashSet<>();
        for (JsonNode s : body.get("suggestions")) {
            all.addAll(ids(s.get("teams").get("A")));
            all.addAll(ids(s.get("teams").get("B")));
        }
        return all;
    }

    private static Set<String> asStrings(List<Row> rows) {
        Set<String> out = new HashSet<>();
        for (Row r : rows) {
            out.add(r.attendanceId().toString());
        }
        return out;
    }

    /** The seeded demo group: 8 READY with bands 1,1,2,2,3,3,4,4 and two free courts. */
    private UUID seedDoubles(List<Row> low, List<Row> high) {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        for (String band : new String[] {"1", "1", "2", "2"}) {
            low.add(ready(game, band));
        }
        for (String band : new String[] {"3", "3", "4", "4"}) {
            high.add(ready(game, band));
        }
        return game;
    }

    // ---- TC-09, TC-10 ----------------------------------------------------------------------------------------

    @Test
    void theSeededGroupFormsTwoSimilarBalancedCourtsAndNothingIsSaved() throws Exception {
        List<Row> low = new ArrayList<>();
        List<Row> high = new ArrayList<>();
        UUID game = seedDoubles(low, high);
        List<Long> before = counts();

        HttpResponse<String> response = hostSuggest(game);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        JsonNode body = json(response);
        assertThat(body.get("suggestions")).hasSize(2);
        JsonNode court1 = body.get("suggestions").get(0);
        JsonNode court2 = body.get("suggestions").get(1);
        assertThat(court1.get("courtNo").asInt()).isEqualTo(1);
        assertThat(court2.get("courtNo").asInt()).isEqualTo(2);
        Set<String> court1Ids = new HashSet<>(ids(court1.get("teams").get("A")));
        court1Ids.addAll(ids(court1.get("teams").get("B")));
        Set<String> court2Ids = new HashSet<>(ids(court2.get("teams").get("A")));
        court2Ids.addAll(ids(court2.get("teams").get("B")));
        assertThat(court1Ids).isEqualTo(asStrings(low));
        assertThat(court2Ids).isEqualTo(asStrings(high));
        for (JsonNode s : body.get("suggestions")) {
            assertThat(s.get("teams").get("A")).hasSize(2);
            assertThat(s.get("teams").get("B")).hasSize(2);
            assertThat(codes(s.get("reasonCodes"))).isEqualTo(Set.of("SIMILAR_LEVEL", "BALANCED_TEAMS"));
            assertThat(s.get("warnings")).isEmpty();
        }
        assertThat(body.get("noSuggestionReason").isNull()).isTrue();
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void theAnswerHasExactlyTheDocumentedFieldsAndNoNamesBandsOrPlayerIds() throws Exception {
        List<Row> low = new ArrayList<>();
        List<Row> high = new ArrayList<>();
        UUID game = seedDoubles(low, high);

        HttpResponse<String> response = hostSuggest(game);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonAllowlist.fieldPaths(response.body()))
                .isEqualTo(Set.of(
                        "suggestions",
                        "suggestions[].courtNo",
                        "suggestions[].teams",
                        "suggestions[].teams.A",
                        "suggestions[].teams.B",
                        "suggestions[].reasonCodes",
                        "suggestions[].warnings",
                        "noSuggestionReason"));
        assertThat(response.body()).doesNotContain("Zorbulon").doesNotContain(host.toString());
        for (UUID playerId : PLAYER.levels.keySet()) {
            assertThat(response.body()).doesNotContain(playerId.toString());
        }
    }

    // ---- TC-11, TC-12, TC-13 ---------------------------------------------------------------------------------

    @Test
    void threeReadyDoublesPlayersGiveWaitingForPlayersAfterOneLevelsCall() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        for (int i = 0; i < 3; i++) {
            ready(game, "2");
        }
        List<Long> before = counts();

        HttpResponse<String> response = hostSuggest(game);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("suggestions")).isEmpty();
        assertThat(body.get("noSuggestionReason").asString()).isEqualTo("WAITING_FOR_PLAYERS");
        assertThat(PLAYER.bodies).hasSize(1);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void aCourtWithAnActiveMatchLeavesNoFreeCourt() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        UUID match = insertMatch(game, 1, 1, "ACTIVE", null);
        for (String team : new String[] {"A", "A", "B", "B"}) {
            matchPlayer(match, game, attend(game, "PLAYING", "2", 0, 0).attendanceId(), team, false);
        }
        for (int i = 0; i < 4; i++) {
            ready(game, "2");
        }

        List<Long> before = counts();

        JsonNode body = json(hostSuggest(game));

        assertThat(body.get("suggestions")).isEmpty();
        assertThat(body.get("noSuggestionReason").asString()).isEqualTo("NO_FREE_COURT");
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void aGameWithNoReadyPlayerAnswersWithoutCallingPlayerService() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        List<Long> before = counts();

        JsonNode body = json(hostSuggest(game));

        assertThat(body.get("suggestions")).isEmpty();
        assertThat(body.get("noSuggestionReason").asString()).isEqualTo("WAITING_FOR_PLAYERS");
        assertThat(PLAYER.bodies).isEmpty();
        assertThat(counts()).isEqualTo(before);
    }

    // ---- TC-14, TC-15 ----------------------------------------------------------------------------------------

    @Test
    void onlyThePartnersOfTheLastThreeFinishedMatchesAreRecentPartners() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        Row p = ready(game, "2");
        List<Row> partners = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Row x = attend(game, "READY", "2", 0, 0);
            partners.add(x);
            UUID match = insertMatch(game, i, 1, "FINISHED", BASE.plusSeconds(i * 60L));
            matchPlayer(match, game, p.attendanceId(), "A", true);
            matchPlayer(match, game, x.attendanceId(), "A", true);
        }
        Row y = attend(game, "READY", "2", 0, 0);
        UUID cancelled = insertMatch(game, 5, 1, "CANCELLED", BASE.plusSeconds(600));
        matchPlayer(cancelled, game, p.attendanceId(), "A", true);
        matchPlayer(cancelled, game, y.attendanceId(), "A", true);

        SuggestionSnapshotQuery.Snapshot snapshot = snapshotQuery.read(game).orElseThrow();

        SuggestionSnapshotQuery.PoolPlayer pooled = snapshot.pool().stream()
                .filter(x -> x.attendanceId().equals(p.attendanceId()))
                .findFirst()
                .orElseThrow();
        assertThat(pooled.recentPartners())
                .containsExactlyInAnyOrder(
                        partners.get(1).attendanceId(),
                        partners.get(2).attendanceId(),
                        partners.get(3).attendanceId());
    }

    @Test
    void twoPlayersWhoWerePartnersInTheLastFinishedMatchAreSplitWhenTeamGapsTie() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        Row a = ready(game, "2");
        Row b = ready(game, "2");
        ready(game, "2");
        ready(game, "2");
        UUID match = insertMatch(game, 1, 1, "FINISHED", BASE.plusSeconds(60));
        matchPlayer(match, game, a.attendanceId(), "A", true);
        matchPlayer(match, game, b.attendanceId(), "A", true);
        List<Long> before = counts();

        JsonNode suggestion = json(hostSuggest(game)).get("suggestions").get(0);
        assertThat(counts()).isEqualTo(before);

        List<String> teamA = ids(suggestion.get("teams").get("A"));
        List<String> teamB = ids(suggestion.get("teams").get("B"));
        boolean aInA = teamA.contains(a.attendanceId().toString());
        boolean bInA = teamA.contains(b.attendanceId().toString());
        assertThat(aInA).isNotEqualTo(bInA);
        assertThat(teamA).hasSize(2);
        assertThat(teamB).hasSize(2);
    }

    // ---- TC-16, TC-17 ----------------------------------------------------------------------------------------

    @Test
    void onlyReadyPlayersOnFreeCourtsAreSuggested() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        UUID planned = insertMatch(game, 1, 1, "PLANNED", null);
        for (String team : new String[] {"A", "A", "B", "B"}) {
            matchPlayer(planned, game, attend(game, "ASSIGNED", "2", 0, 0).attendanceId(), team, false);
        }
        attend(game, "PENDING", "2", 0, 0);
        attend(game, "LEFT", "2", 0, 0);
        attend(game, "RESTING", "2", 0, 0);
        List<Row> readyRows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            readyRows.add(ready(game, "2"));
        }
        List<Long> before = counts();

        JsonNode body = json(hostSuggest(game));

        assertThat(body.get("suggestions")).hasSize(1);
        assertThat(body.get("suggestions").get(0).get("courtNo").asInt()).isEqualTo(2);
        assertThat(suggestedIds(body)).isEqualTo(asStrings(readyRows));
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void aReadyPlayerStillInAnUnreleasedMatchIsNotSuggested() throws Exception {
        UUID game = insertGame(2, "DOUBLES", "OPEN");
        Row inconsistent = ready(game, "2");
        UUID planned = insertMatch(game, 1, 1, "PLANNED", null);
        matchPlayer(planned, game, inconsistent.attendanceId(), "A", false);
        List<Row> clean = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            clean.add(ready(game, "2"));
        }
        List<Long> before = counts();

        JsonNode body = json(hostSuggest(game));

        assertThat(body.get("suggestions")).hasSize(1);
        assertThat(suggestedIds(body)).isEqualTo(asStrings(clean));
        assertThat(suggestedIds(body))
                .doesNotContain(inconsistent.attendanceId().toString());
        assertThat(counts()).isEqualTo(before);
    }

    // ---- TC-18, TC-19 ----------------------------------------------------------------------------------------

    @Test
    void aClosedGameIs409AndNothingIsCalledOrChanged() throws Exception {
        UUID game = insertGame(1, "DOUBLES", "CLOSED");
        for (int i = 0; i < 4; i++) {
            ready(game, "2");
        }
        List<Long> before = counts();

        HttpResponse<String> response = hostSuggest(game);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(JsonAllowlist.fieldPaths(response.body())).isEqualTo(Set.of("code", "message", "requestId"));
        assertThat(PLAYER.bodies).isEmpty();
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void otherAccountsMissingOrWrongTokensAndUnknownIdsAreRefusedWithoutCallingPlayerService() throws Exception {
        List<Row> low = new ArrayList<>();
        List<Row> high = new ArrayList<>();
        UUID game = seedDoubles(low, high);
        String g = game.toString();
        String boardToken =
                new IdentityToken(SECRET, CLOCK).issue(host, "playmatch-service", "GET", "/api/v1/host/games/" + g);
        String unknown = UUID.randomUUID().toString();

        HttpResponse<String> other = suggest(g, tokenFor(UUID.randomUUID(), g));
        HttpResponse<String> none = suggest(g, null);
        HttpResponse<String> wrongPath = suggest(g, boardToken);
        HttpResponse<String> unknownGame = suggest(unknown, tokenFor(host, unknown));
        HttpResponse<String> malformed = suggest("not-a-uuid", tokenFor(host, "not-a-uuid"));

        assertThat(other.statusCode()).isEqualTo(403);
        assertThat(other.body()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(none.statusCode()).isEqualTo(401);
        assertThat(wrongPath.statusCode()).isEqualTo(401);
        assertThat(unknownGame.statusCode()).isEqualTo(404);
        assertThat(malformed.statusCode()).isEqualTo(404);
        assertThat(PLAYER.bodies).isEmpty();
    }

    // ---- TC-20, TC-21, TC-22 ---------------------------------------------------------------------------------

    @Test
    void aSinglesGameGetsOnePlayerPerTeamOnTwoCourtsWithNoIdTwice() throws Exception {
        UUID game = insertGame(2, "SINGLES", "OPEN");
        for (int i = 0; i < 5; i++) {
            ready(game, "2");
        }
        List<Long> before = counts();

        JsonNode body = json(hostSuggest(game));

        assertThat(body.get("suggestions")).hasSize(2);
        for (JsonNode s : body.get("suggestions")) {
            assertThat(s.get("teams").get("A")).hasSize(1);
            assertThat(s.get("teams").get("B")).hasSize(1);
        }
        assertThat(suggestedIds(body)).hasSize(4);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void whenPlayerServiceFailsOrDoesNotAnswerInTimeTheAnswerIs503WithNoSuggestions() throws Exception {
        List<Row> low = new ArrayList<>();
        List<Row> high = new ArrayList<>();
        UUID game = seedDoubles(low, high);
        List<Long> before = counts();

        PLAYER.failStatus = 500;
        HttpResponse<String> failed = hostSuggest(game);
        PLAYER.failStatus = 0;
        PLAYER.stallMillis = 1500;
        HttpResponse<String> slow = hostSuggest(game);

        for (HttpResponse<String> response : List.of(failed, slow)) {
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
            assertThat(JsonAllowlist.fieldPaths(response.body())).isEqualTo(Set.of("code", "message", "requestId"));
        }
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void aReadyPlayerMissingFromTheLevelsAnswerIsUnknownAndLogsOnlyTheAttendanceId(CapturedOutput output)
            throws Exception {
        UUID game = insertGame(1, "DOUBLES", "OPEN");
        Row unknown = ready(game, "2");
        for (int i = 0; i < 3; i++) {
            ready(game, "2");
        }
        PLAYER.omit.add(unknown.playerId());
        List<Long> before = counts();

        HttpResponse<String> response = hostSuggest(game);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(counts()).isEqualTo(before);
        JsonNode suggestion = json(response).get("suggestions").get(0);
        assertThat(codes(suggestion.get("warnings"))).contains("UNKNOWN_LEVEL");
        assertThat(output.getAll()).contains(unknown.attendanceId().toString());
        assertThat(output.getAll()).doesNotContain("Zorbulon");
        assertThat(output.getAll().lines().filter(l -> l.contains("No level from player-service")))
                .hasSize(1);
    }
}
