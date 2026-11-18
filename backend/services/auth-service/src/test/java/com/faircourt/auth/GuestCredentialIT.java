package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guest credentials, auth-service side (blueprint section 12; OWASP Session Management Cheat Sheet): the internal routes
 * {@code POST /internal/v1/auth/guest-credentials} and {@code .../resolve} against PostgreSQL over real HTTP. Only the
 * SHA-256 of a token is stored; unknown, expired and revoked tokens get one identical answer; nothing is logged.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@Import(TestMailConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class GuestCredentialIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestMailConfig.MovableClock clock;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final UUID game = UUID.randomUUID();

    @BeforeEach
    void resetTestSchema() {
        TestDatabase.reset(jdbc, flyway);
        clock.sync();
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String issueJson(UUID attendance, Instant expiresAt) {
        return "{\"gameId\":\"" + game + "\",\"attendanceId\":\"" + attendance + "\",\"expiresAt\":\"" + expiresAt
                + "\"}";
    }

    private String issue(UUID attendance, Instant expiresAt) throws Exception {
        HttpResponse<String> response = post("/internal/v1/auth/guest-credentials", issueJson(attendance, expiresAt));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return JSON.readTree(response.body()).get("token").asString();
    }

    private HttpResponse<String> resolve(String token) throws Exception {
        return post("/internal/v1/auth/guest-credentials/resolve", "{\"token\":\"" + token + "\"}");
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void issueAnswersTheRawTokenOnceAndStoresOnlyItsSha256() throws Exception {
        UUID attendance = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofHours(14));

        HttpResponse<String> response = post("/internal/v1/auth/guest-credentials", issueJson(attendance, expiresAt));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonAllowlist.assertOnlyFields(response.body(), "token", "expiresAt");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        String token = JSON.readTree(response.body()).get("token").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        List<Map<String, Object>> rows =
                jdbc.sql("SELECT * FROM guest_access").query().listOfRows();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("token_hash")).isEqualTo(sha256(token));
        assertThat(row.get("token_hash").toString()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(row.get("game_id")).isEqualTo(game);
        assertThat(row.get("attendance_id")).isEqualTo(attendance);
        assertThat(row.get("revoked_at")).isNull();
        // The raw token is found in no column of the table.
        assertThat(row.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(token));
    }

    @Test
    void twoCredentialsAreDifferentAndAnewOneForTheSameAttendanceRevokesTheOlder() throws Exception {
        UUID attendance = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofHours(14));

        String first = issue(attendance, expiresAt);
        String second = issue(attendance, expiresAt);

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM guest_access WHERE revoked_at IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM guest_access")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(resolve(first).statusCode()).isEqualTo(401);
        assertThat(resolve(second).statusCode()).isEqualTo(200);
    }

    @Test
    void aValidTokenResolvesToItsGameAndAttendanceOnly() throws Exception {
        UUID attendance = UUID.randomUUID();
        String token = issue(attendance, clock.instant().plus(Duration.ofHours(14)));

        HttpResponse<String> response = resolve(token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), "gameId", "attendanceId");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("gameId").asString()).isEqualTo(game.toString());
        assertThat(body.get("attendanceId").asString()).isEqualTo(attendance.toString());
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void unknownExpiredRevokedAndMalformedTokensGetOneIdenticalAnswer() throws Exception {
        UUID attendance = UUID.randomUUID();
        String expired = issue(attendance, clock.instant().plus(Duration.ofMinutes(5)));
        clock.advance(Duration.ofMinutes(6));
        String revoked = issue(UUID.randomUUID(), clock.instant().plus(Duration.ofHours(1)));
        jdbc.sql("UPDATE guest_access SET revoked_at = now() WHERE token_hash = :hash")
                .param("hash", sha256(revoked))
                .update();
        String unknown = "A".repeat(43);

        List<HttpResponse<String>> answers = List.of(
                resolve(expired), resolve(revoked), resolve(unknown), resolve("short"), resolve("not valid ' OR 1=1"));

        for (HttpResponse<String> answer : answers) {
            assertThat(answer.statusCode()).isEqualTo(401);
            assertThat(answer.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        }
        assertThat(answers.stream().map(a -> a.body().replaceAll("\"requestId\":\"[^\"]*\"", "")))
                .containsOnly(answers.getFirst().body().replaceAll("\"requestId\":\"[^\"]*\"", ""));
    }

    @Test
    void anEndDateInThePastIsRefusedAndWritesNothing() throws Exception {
        HttpResponse<String> response = post(
                "/internal/v1/auth/guest-credentials",
                issueJson(UUID.randomUUID(), clock.instant().minusSeconds(1)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(jdbc.sql("SELECT count(*) FROM guest_access")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void badBodiesAre400AndWriteNothing() throws Exception {
        for (String json : new String[] {"{}", "{not json", "{\"gameId\":\"x\"}"}) {
            assertThat(post("/internal/v1/auth/guest-credentials", json).statusCode())
                    .as(json)
                    .isEqualTo(400);
        }
        assertThat(post("/internal/v1/auth/guest-credentials/resolve", "{}").statusCode())
                .isEqualTo(400);
        assertThat(jdbc.sql("SELECT count(*) FROM guest_access")
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void theRawTokenAppearsInNoLogLine(CapturedOutput output) throws Exception {
        String token = issue(UUID.randomUUID(), clock.instant().plus(Duration.ofHours(14)));
        resolve(token);
        resolve("B".repeat(43));

        assertThat(output.getAll()).doesNotContain(token);
    }
}
