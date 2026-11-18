package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import com.faircourt.common.testsupport.TestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Account login, auth-service side (blueprint sections 13, 15; OWASP Authentication Cheat Sheet): the internal route
 * {@code POST /internal/v1/auth/verify} against PostgreSQL over real HTTP. Unknown email and wrong password must be
 * indistinguishable; nothing secret is ever returned.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
class AccountVerifyIT {

    private static final String EMAIL = "Host@FairCourt.local";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private UUID userId;

    @BeforeEach
    void resetAndSeedOneAccount() {
        TestDatabase.reset(jdbc, flyway);
        userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (id, email, password_hash) VALUES (:id, :email, :hash)")
                .param("id", userId)
                .param("email", EMAIL)
                .param("hash", passwordEncoder.encode(PASSWORD))
                .update();
    }

    private HttpResponse<String> verify(String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/auth/verify"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void rightPasswordReturnsOnlyTheAccountIdentity() throws Exception {
        HttpResponse<String> response = verify(body("host@faircourt.local", PASSWORD));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), "userId", "email");
        assertThat(response.body()).contains(userId.toString(), EMAIL);
        assertThat(response.body()).doesNotContain("password", "hash", "$2");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void aPasswordOfOnlySpacesIsAWrongPasswordNot400() throws Exception {
        assertThat(verify(body("host@faircourt.local", "      ")).statusCode()).isEqualTo(401);
    }

    @Test
    void emailMatchingIgnoresCaseAndSurroundingSpaces() throws Exception {
        assertThat(verify(body("  HOST@faircourt.LOCAL ", PASSWORD)).statusCode())
                .isEqualTo(200);
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameAnswer() throws Exception {
        HttpResponse<String> wrongPassword = verify(body("host@faircourt.local", "not the password"));
        HttpResponse<String> unknownEmail = verify(body("nobody@faircourt.local", PASSWORD));

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.body().replaceAll("\"requestId\":\"[^\"]*\"", ""))
                .isEqualTo(wrongPassword.body().replaceAll("\"requestId\":\"[^\"]*\"", ""));
        assertThat(wrongPassword.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void anUnknownEmailStillPaysForAPasswordHash() throws Exception {
        // Warm up (class loading, connection) so the comparison below is not about start-up cost.
        verify(body("host@faircourt.local", "warm up"));
        verify(body("nobody@faircourt.local", "warm up"));

        long unknown = timeOf(() -> verify(body("nobody@faircourt.local", "x")));
        long wrong = timeOf(() -> verify(body("host@faircourt.local", "x")));

        // BCrypt at cost 10 takes tens of milliseconds; a skipped hash would take a few. Not a precise timing test.
        assertThat(unknown)
                .as("unknown email should not return much faster than a wrong password")
                .isGreaterThan(wrong / 3);
    }

    private interface Call {
        void run() throws Exception;
    }

    private static long timeOf(Call call) throws Exception {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            call.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    @Test
    void badInputIs400BeforeAnyHashing() throws Exception {
        String tooLongForBcrypt = "a".repeat(73);

        assertThat(verify(body("host@faircourt.local", tooLongForBcrypt)).statusCode())
                .isEqualTo(400);
        assertThat(verify(body("host@faircourt.local", "é".repeat(37))).statusCode())
                .isEqualTo(400); // 74 bytes
        assertThat(verify(body("", "x")).statusCode()).isEqualTo(400);
        assertThat(verify(body("host@faircourt.local", "")).statusCode()).isEqualTo(400);
        assertThat(verify(body("a".repeat(255), "x")).statusCode()).isEqualTo(400);
        assertThat(verify("{not json").statusCode()).isEqualTo(400);
    }

    @Test
    void onlyJsonPostIsAccepted() throws Exception {
        HttpResponse<String> form = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/auth/verify"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("email=host@faircourt.local&password=" + PASSWORD))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> get = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/internal/v1/auth/verify"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(form.statusCode()).isGreaterThanOrEqualTo(400).isLessThan(500);
        assertThat(form.statusCode()).isNotEqualTo(200);
        assertThat(get.statusCode()).isEqualTo(401);
    }

    @Test
    void failuresDoNotLeakThePasswordOrTheHash() throws Exception {
        HttpResponse<String> response = verify(body("host@faircourt.local", "secret-guess-123"));

        assertThat(response.body()).doesNotContain("secret-guess-123", "$2", EMAIL);
    }
}
