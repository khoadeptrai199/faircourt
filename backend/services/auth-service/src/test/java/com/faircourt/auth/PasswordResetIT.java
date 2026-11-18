package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password reset by emailed one-time code, auth-service side (owner approved 2026-11-18; OWASP Forgot Password Cheat
 * Sheet): over real HTTP against PostgreSQL, mail recorded by {@link TestMailConfig}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
@Import(TestMailConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetIT {

    private static final String EMAIL = "Host@FairCourt.local";
    private static final String OLD_PASSWORD = "old password of the host";
    private static final String NEW_PASSWORD = "a brand new passphrase";
    private static final Pattern CODE = Pattern.compile("code is (\\d{8})\\.");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TestMailConfig.RecordingMailSender mail;

    @Autowired
    TestMailConfig.MovableClock clock;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID userId;

    @BeforeEach
    void resetAndSeedOneAccount() {
        TestDatabase.reset(jdbc, flyway);
        mail.clear();
        userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (id, email, password_hash) VALUES (:id, :email, :hash)")
                .param("id", userId)
                .param("email", EMAIL)
                .param("hash", passwordEncoder.encode(OLD_PASSWORD))
                .update();
        clock.sync(); // after the insert, so the clock is never earlier than the database default `now()`
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> forgot(String email) throws Exception {
        return post("/api/v1/auth/password/forgot", "{\"email\":\"" + email + "\"}");
    }

    private HttpResponse<String> reset(String email, String code, String password) throws Exception {
        return post(
                "/api/v1/auth/password/reset",
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + password + "\"}");
    }

    private HttpResponse<String> verify(String email, String password) throws Exception {
        return post("/internal/v1/auth/verify", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    /** The code from the newest mail. */
    private String lastCode() {
        SimpleMailMessage message = mail.sent.get(mail.sent.size() - 1);
        Matcher m = CODE.matcher(message.getText());
        assertThat(m.find()).as(message.getText()).isTrue();
        return m.group(1);
    }

    private long rows() {
        return jdbc.sql("SELECT count(*) FROM password_reset").query(Long.class).single();
    }

    private static String sameWithoutRequestId(String body) {
        return body.replaceAll("\"requestId\":\"[^\"]*\"", "");
    }

    @Test
    void knownAndUnknownEmailsGetTheSameAnswerAndOnlyTheKnownOneGetsAMail() throws Exception {
        HttpResponse<String> known = forgot("host@faircourt.local");
        HttpResponse<String> unknown = forgot("nobody@faircourt.local");

        assertThat(known.statusCode()).isEqualTo(202);
        assertThat(unknown.statusCode()).isEqualTo(202);
        assertThat(known.body()).isEqualTo("{}").isEqualTo(unknown.body());
        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).getTo()).containsExactly(EMAIL);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void theMailHoldsAnEightDigitCodeAndTheDatabaseOnlyAHash() throws Exception {
        forgot(EMAIL);

        String code = lastCode();

        assertThat(code).matches("\\d{8}");
        String stored = jdbc.sql("SELECT code_hash FROM password_reset")
                .query(String.class)
                .single();
        assertThat(stored).doesNotContain(code).contains("{bcrypt}$2");
        assertThat(mail.sent.get(0).getText()).contains("10 minutes").doesNotContain(OLD_PASSWORD);
    }

    @Test
    void aRightCodeChangesThePasswordOnceAndSendsAChangedMail() throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        mail.sent.clear();
        Instant before = jdbc.sql("SELECT password_changed_at FROM app_user")
                .query(java.time.OffsetDateTime.class)
                .single()
                .toInstant();
        clock.advance(Duration.ofSeconds(5));

        HttpResponse<String> response = reset("host@faircourt.local", code, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(verify(EMAIL, NEW_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(verify(EMAIL, OLD_PASSWORD).statusCode()).isEqualTo(401);
        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).getText()).contains("was changed").doesNotContain(NEW_PASSWORD);
        Instant after = jdbc.sql("SELECT password_changed_at FROM app_user")
                .query(java.time.OffsetDateTime.class)
                .single()
                .toInstant();
        assertThat(after).isAfter(before);
        // The code works only once.
        assertThat(reset(EMAIL, code, "another passphrase again").statusCode()).isEqualTo(401);
        assertThat(verify(EMAIL, NEW_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void everyKindOfBadCodeGivesTheSameAnswer() throws Exception {
        // wrong code
        forgot(EMAIL);
        String first = lastCode();
        HttpResponse<String> wrong = reset(EMAIL, other(first), NEW_PASSWORD);
        // unknown email
        HttpResponse<String> unknown = reset("nobody@faircourt.local", first, NEW_PASSWORD);
        // replaced: a newer code cancels the older one
        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL);
        HttpResponse<String> replaced = reset(EMAIL, first, NEW_PASSWORD);
        // expired
        String second = lastCode();
        clock.advance(Duration.ofMinutes(11));
        HttpResponse<String> expired = reset(EMAIL, second, NEW_PASSWORD);

        for (HttpResponse<String> response : List.of(wrong, unknown, replaced, expired)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        }
        assertThat(sameWithoutRequestId(unknown.body()))
                .isEqualTo(sameWithoutRequestId(wrong.body()))
                .isEqualTo(sameWithoutRequestId(replaced.body()))
                .isEqualTo(sameWithoutRequestId(expired.body()));
        assertThat(verify(EMAIL, OLD_PASSWORD).statusCode()).isEqualTo(200);
    }

    private static String other(String code) {
        return code.equals("00000000") ? "11111111" : "00000000";
    }

    @Test
    void afterFiveWrongTriesEvenTheRightCodeIsDead() throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        for (int i = 0; i < 5; i++) {
            assertThat(reset(EMAIL, other(code), NEW_PASSWORD).statusCode()).isEqualTo(401);
        }

        assertThat(reset(EMAIL, code, NEW_PASSWORD).statusCode()).isEqualTo(401);
        assertThat(verify(EMAIL, OLD_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void parallelWrongGuessesNeverGetMoreThanFiveTriesPerCode() throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return reset(EMAIL, other(code), NEW_PASSWORD).statusCode();
            }));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(401);
        }
        pool.shutdown();

        assertThat(jdbc.sql("SELECT failed_attempts FROM password_reset")
                        .query(Integer.class)
                        .single())
                .isEqualTo(5);
        assertThat(reset(EMAIL, code, NEW_PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void onlyOneCodePerMinuteAndThreePerHourAndTheAnswerNeverChanges() throws Exception {
        assertThat(forgot(EMAIL).statusCode()).isEqualTo(202);
        assertThat(forgot(EMAIL).statusCode()).isEqualTo(202); // within 60 s: no mail
        assertThat(mail.sent).hasSize(1);
        assertThat(rows()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL);
        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL);
        assertThat(mail.sent).hasSize(3);

        clock.advance(Duration.ofSeconds(61));
        HttpResponse<String> fourth = forgot(EMAIL);

        assertThat(fourth.statusCode()).isEqualTo(202);
        assertThat(fourth.body()).isEqualTo("{}");
        assertThat(mail.sent).as("a 4th code within one hour is not sent").hasSize(3);
        assertThat(rows()).isEqualTo(3);

        clock.advance(Duration.ofHours(1));
        forgot(EMAIL);
        assertThat(mail.sent).hasSize(4);
    }

    @Test
    void aNewCodeCancelsTheOlderOnes() throws Exception {
        forgot(EMAIL);
        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL);

        assertThat(jdbc.sql("SELECT count(*) FROM password_reset WHERE consumed_at IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void weakOrMalformedInputIs400AndChangesNothing() throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        String[] badPasswords = {
            "short1234",
            "elevenchars",
            "x".repeat(73),
            "aaaaaaaaaaaaaaaa",
            "Password1234",
            "host@faircourt.local",
            "é".repeat(37) // 74 bytes
        };
        for (String password : badPasswords) {
            assertThat(reset(EMAIL, code, password).statusCode()).as(password).isEqualTo(400);
        }
        assertThat(reset(EMAIL, "1234567", NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(reset(EMAIL, "abcdefgh", NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(reset(EMAIL, "123456789", NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(forgot("").statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/auth/password/forgot", "{not json").statusCode())
                .isEqualTo(400);

        // The code was not used up by the refused requests, and the password is unchanged.
        assertThat(verify(EMAIL, OLD_PASSWORD).statusCode()).isEqualTo(200);
        assertThat(reset(EMAIL, code, NEW_PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test
    void aBrokenMailServerStillGets202AndNothingLeaks() throws Exception {
        mail.failing = true;

        HttpResponse<String> response = forgot(EMAIL);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("{}");
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void neitherTheCodeNorAPasswordIsEverLogged(CapturedOutput output) throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        reset(EMAIL, other(code), "wrong try passphrase");
        reset(EMAIL, code, NEW_PASSWORD);
        mail.failing = true;
        clock.advance(Duration.ofSeconds(61));
        forgot(EMAIL);

        assertThat(output.getAll()).doesNotContain(code, NEW_PASSWORD, "wrong try passphrase", OLD_PASSWORD, "$2a$");
    }

    @Test
    void anUnknownEmailPaysForTheSameHashingWorkAsAKnownOne() throws Exception {
        forgot(EMAIL); // warm up
        forgot("nobody@faircourt.local");

        long unknown = best(() -> forgot("nobody@faircourt.local"));
        long known = best(() -> {
            clock.advance(Duration.ofHours(2)); // outside the request limits, so a mail is really sent
            forgot(EMAIL);
        });

        // BCrypt takes tens of milliseconds; a skipped hash would take a few. Not a precise timing test.
        assertThat(unknown).as("unknown email must not answer much faster").isGreaterThan(known / 3);
    }

    private interface Call {
        void run() throws Exception;
    }

    private static long best(Call call) throws Exception {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            call.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    @Test
    void resetForAnUnknownEmailPaysForTheSameHashingWorkAsForAKnownOne() throws Exception {
        forgot(EMAIL);
        String code = lastCode();
        reset(EMAIL, other(code), NEW_PASSWORD); // warm up
        reset("nobody@faircourt.local", other(code), NEW_PASSWORD);

        long unknown = best(() -> reset("nobody@faircourt.local", other(code), NEW_PASSWORD));
        long known = best(() -> reset(EMAIL, other(code), NEW_PASSWORD));

        // Both do two BCrypt steps (about 100 ms each); before the fix the unknown email took about half the time.
        assertThat(unknown).as("unknown email must not answer much faster").isGreaterThan(known * 7 / 10);
    }

    @Test
    void aCodeOfOneAccountDoesNothingForAnotherAccount() throws Exception {
        String other = "second@faircourt.local";
        jdbc.sql("INSERT INTO app_user (id, email, password_hash) VALUES (:id, :email, :hash)")
                .param("id", UUID.randomUUID())
                .param("email", other)
                .param("hash", passwordEncoder.encode("second accounts password"))
                .update();
        forgot(EMAIL);
        String codeOfHost = lastCode();

        HttpResponse<String> response = reset(other, codeOfHost, NEW_PASSWORD);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(verify(other, "second accounts password").statusCode()).isEqualTo(200);
        assertThat(verify(EMAIL, OLD_PASSWORD).statusCode()).isEqualTo(200);
        // The host's code is still usable by the host.
        assertThat(reset(EMAIL, codeOfHost, NEW_PASSWORD).statusCode()).isEqualTo(204);
    }

    @Test
    void lineBreaksInTheEmailFieldCannotReachTheMail() throws Exception {
        String injected = "host@faircourt.local\\r\\nBcc: attacker@evil.test";

        HttpResponse<String> response = forgot(injected);

        // Not an account (the email does not match), so no mail at all; and a real address is never taken from the
        // request.
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(mail.sent).isEmpty();
        forgot(EMAIL);
        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).getTo()).containsExactly(EMAIL);
        assertThat(mail.sent.get(0).getBcc()).isNull();
    }

    @Test
    void everyAnswerOfTheTwoRoutesCarriesNoStore() throws Exception {
        forgot(EMAIL);
        String code = lastCode();

        for (HttpResponse<String> response : List.of(
                forgot(EMAIL),
                forgot(""),
                reset(EMAIL, other(code), NEW_PASSWORD),
                reset(EMAIL, "123", NEW_PASSWORD),
                reset(EMAIL, code, NEW_PASSWORD))) {
            assertThat(response.headers().firstValue("Cache-Control"))
                    .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        }
    }

    @Test
    void sessionCheckSaysInvalidOnceThePasswordChangedAfterTheLogin() throws Exception {
        Instant loginBeforeReset = clock.instant(); // the person logged in after the account existed
        forgot(EMAIL);
        clock.advance(Duration.ofSeconds(5));
        assertThat(sessionCheck(userId, loginBeforeReset)).contains("\"valid\":true");

        reset(EMAIL, lastCode(), NEW_PASSWORD);

        assertThat(sessionCheck(userId, loginBeforeReset)).contains("\"valid\":false");
        assertThat(sessionCheck(userId, clock.instant().plusSeconds(60))).contains("\"valid\":true");
        assertThat(sessionCheck(UUID.randomUUID(), clock.instant())).contains("\"valid\":false");
    }

    @Test
    void sessionCheckRefusesBadInput() throws Exception {
        HttpResponse<String> badInstant = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/auth/accounts/" + userId
                                + "/session-check?since=yesterday"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> badId = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port
                                + "/internal/v1/auth/accounts/not-a-uuid/session-check?since="
                                + Instant.now()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(badInstant.statusCode()).isEqualTo(400);
        assertThat(badId.statusCode()).isEqualTo(400);
    }

    private String sessionCheck(UUID id, Instant since) throws Exception {
        return http.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/auth/accounts/"
                                        + id + "/session-check?since=" + since))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString())
                .body();
    }
}
