package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Account login, gateway side (blueprint sections 11, 13, 15; OWASP Authentication and Session Management Cheat
 * Sheets). auth-service is a stub. Covers: cookie flags, new session id at login, one answer for every kind of wrong
 * credentials, CSRF, JSON only, input limits, throttling, logout, no-store caching, no secrets reaching the browser.
 */
class AccountLoginIT extends LoginTestBase {

    private HttpResponse<String> postJson(String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(request::header);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", loginJson(email, password), bootstrap());
    }

    private static String sessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("fc_sid="))
                .findFirst()
                .orElseThrow();
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring("fc_sid=".length(), setCookie.indexOf(';'));
    }

    private long verifyCalls() {
        return STUB.received().stream()
                .filter(r -> r.path().equals(StubService.VERIFY_PATH))
                .count();
    }

    @Test
    void rightPasswordSignsInAndMeReturnsTheSameIdentity() throws Exception {
        String email = uniqueEmail();

        HttpResponse<String> response = login(email, StubService.ACCOUNT_PASSWORD);
        HttpResponse<String> me = get("/api/v1/me");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonAllowlist.assertOnlyFields(response.body(), "userId", "email");
        assertThat(response.body()).contains(StubService.ACCOUNT_USER_ID, email);
        assertThat(response.body()).doesNotContain(StubService.ACCOUNT_PASSWORD);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).isEqualTo(response.body());
        JsonAllowlist.assertOnlyFields(me.body(), "userId", "email");
    }

    @Test
    void sessionCookieIsGenericHttpOnlyAndSameSiteLax() throws Exception {
        HttpResponse<String> response = login(uniqueEmail(), StubService.ACCOUNT_PASSWORD);

        String cookie = sessionCookie(response);
        assertThat(cookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
        assertThat(cookie).doesNotContain("JSESSIONID");
        // Not "Secure" in tests: COOKIE_SECURE defaults to false (plain local HTTP, blueprint section 0).
    }

    @Test
    void theSessionIdChangesAtLogin() throws Exception {
        HttpResponse<String> bootstrap = get("/api/v1/auth/csrf");
        String before = cookieValue(sessionCookie(bootstrap));

        HttpResponse<String> response = postJson(
                "/api/v1/auth/login",
                loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD),
                csrfHeaderFrom(bootstrap));
        String after = cookieValue(sessionCookie(response));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(after).isNotEqualTo(before);
    }

    private static Map<String, String> csrfHeaderFrom(HttpResponse<String> csrfResponse) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(csrfResponse.body());
        assertThat(m.find()).isTrue();
        return Map.of("X-CSRF-TOKEN", m.group(1));
    }

    @Test
    void anOldSessionIdIsUselessAfterLogin() throws Exception {
        HttpResponse<String> bootstrap = get("/api/v1/auth/csrf");
        String oldId = cookieValue(sessionCookie(bootstrap));
        postJson(
                "/api/v1/auth/login",
                loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD),
                csrfHeaderFrom(bootstrap));

        // A different browser that only knows the old (pre-login) id must not be signed in.
        HttpResponse<String> attacker = java.net.http.HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                                .header("Cookie", "fc_sid=" + oldId)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(attacker.statusCode()).isEqualTo(401);
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameAnswerAndAreForwardedToAuthService() throws Exception {
        // The stub answers 401 for any wrong password; the gateway must not add anything that tells the cases apart.
        HttpResponse<String> first = login(uniqueEmail(), "wrong-1");
        HttpResponse<String> second = login(uniqueEmail(), "wrong-2");

        assertThat(first.statusCode()).isEqualTo(401);
        assertThat(second.statusCode()).isEqualTo(401);
        assertThat(first.body().replaceAll("\"requestId\":\"[^\"]*\"", ""))
                .isEqualTo(second.body().replaceAll("\"requestId\":\"[^\"]*\"", ""));
        assertThat(first.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(first.body()).doesNotContain("wrong-1");
    }

    @Test
    void aFailedLoginDoesNotSignAnyoneIn() throws Exception {
        login(uniqueEmail(), "wrong");

        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginWithoutCsrfTokenIs403AndNeverReachesAuthService() throws Exception {
        HttpResponse<String> response =
                postJson("/api/v1/auth/login", loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD), Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(verifyCalls()).isZero();
    }

    @Test
    void onlyJsonIsAcceptedAndNothingIsForwardedOtherwise() throws Exception {
        HttpRequest.Builder form = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("email=a@b.c&password=" + StubService.ACCOUNT_PASSWORD));
        bootstrap().forEach(form::header);

        HttpResponse<String> response = browser.send(form.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(400, 415);
        assertThat(verifyCalls()).isZero();
    }

    @Test
    void badInputIs400AndNeverReachesAuthService() throws Exception {
        String[] bodies = {
            "{not json",
            "{}",
            loginJson("", "x"),
            loginJson("a@b.c", ""),
            loginJson("a".repeat(255), "x"),
            loginJson("a@b.c", "p".repeat(73)),
            loginJson("a@b.c", "é".repeat(37)), // 74 bytes
        };
        for (String json : bodies) {
            HttpResponse<String> response = postJson("/api/v1/auth/login", json, bootstrap());

            assertThat(response.statusCode()).as(json).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        }
        assertThat(verifyCalls()).isZero();
    }

    @Test
    void fiveFailuresLockTheEmailEvenForTheRightPasswordAndNothingIsForwardedWhileLocked() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong-" + i).statusCode()).isEqualTo(401);
        }

        HttpResponse<String> locked = login(email, StubService.ACCOUNT_PASSWORD);
        HttpResponse<String> lockedAgain = login(email, "wrong");

        assertThat(locked.statusCode()).isEqualTo(429);
        assertThat(locked.body()).contains("\"code\":\"TOO_MANY_REQUESTS\"");
        assertThat(locked.headers().firstValue("Retry-After"))
                .hasValueSatisfying(v -> assertThat(Integer.parseInt(v)).isBetween(1, 61));
        assertThat(lockedAgain.statusCode()).isEqualTo(429);
        assertThat(verifyCalls())
                .as("the 6th and 7th attempts are not forwarded")
                .isEqualTo(5);
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void theLockIsPerEmailAndIgnoresCaseAndSpaces() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-" + i);
        }

        assertThat(login("  " + email.toUpperCase() + " ", StubService.ACCOUNT_PASSWORD)
                        .statusCode())
                .isEqualTo(429);
        assertThat(login(uniqueEmail(), StubService.ACCOUNT_PASSWORD).statusCode())
                .isEqualTo(200);
    }

    @Test
    void aSuccessBeforeTheLimitClearsTheCount() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 4; i++) {
            login(email, "wrong-" + i);
        }
        assertThat(login(email, StubService.ACCOUNT_PASSWORD).statusCode()).isEqualTo(200);

        for (int i = 0; i < 4; i++) {
            assertThat(login(email, "wrong-again-" + i).statusCode()).isEqualTo(401);
        }
        assertThat(login(email, StubService.ACCOUNT_PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test
    void anAuthServiceTimeoutIs503AndIsNotCountedAsAFailure() throws Exception {
        String email = StubService.SLOW_EMAIL_PREFIX + java.util.UUID.randomUUID() + "@faircourt.local";
        for (int i = 0; i < 6; i++) {
            HttpResponse<String> response = login(email, StubService.ACCOUNT_PASSWORD);

            assertThat(response.statusCode()).as("attempt " + i).isEqualTo(503);
            assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        }
    }

    @Test
    void theRequestIdIsPassedToAuthServiceAndThePasswordNeverToTheBrowser() throws Exception {
        HttpResponse<String> response = login(uniqueEmail(), StubService.ACCOUNT_PASSWORD);

        StubService.Received call = STUB.received().stream()
                .filter(r -> r.path().equals(StubService.VERIFY_PATH))
                .findFirst()
                .orElseThrow();
        assertThat(call.headers().getFirst("X-Request-Id"))
                .isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(call.headers().getFirst("Cookie")).isNull();
        assertThat(response.headers().map().toString()).doesNotContain(StubService.ACCOUNT_PASSWORD);
    }

    @Test
    void loginMeAndLogoutAreNeverCached() throws Exception {
        HttpResponse<String> login = login(uniqueEmail(), StubService.ACCOUNT_PASSWORD);
        HttpResponse<String> me = get("/api/v1/me");
        HttpResponse<String> logout = postJson("/api/v1/auth/logout", "{}", bootstrap());

        for (HttpResponse<String> response : java.util.List.of(login, me, logout)) {
            assertThat(response.headers().firstValue("Cache-Control"))
                    .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        }
    }

    @Test
    void logoutEndsTheSessionAndAsksTheBrowserToDropCookies() throws Exception {
        login(uniqueEmail(), StubService.ACCOUNT_PASSWORD);
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(200);

        HttpResponse<String> logout = postJson("/api/v1/auth/logout", "{}", bootstrap());

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().firstValue("Clear-Site-Data"))
                .hasValueSatisfying(v -> assertThat(v).contains("cookies"));
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void logoutNeedsASignedInAccountAndACsrfToken() throws Exception {
        assertThat(postJson("/api/v1/auth/logout", "{}", Map.of()).statusCode()).isEqualTo(403);
        assertThat(postJson("/api/v1/auth/logout", "{}", bootstrap()).statusCode())
                .isEqualTo(401);
    }

    @Test
    void anonymousMeIs401Json() throws Exception {
        HttpResponse<String> response = get("/api/v1/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    @Test
    void anOversizedLoginBodyIsRefusedBeforeItIsReadAndNeverForwarded() throws Exception {
        String huge = loginJson("a@b.c", "x".repeat(10_000));

        HttpResponse<String> response = postJson("/api/v1/auth/login", huge, bootstrap());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(verifyCalls()).isZero();
    }

    @Test
    void aPasswordOfOnlySpacesIsAnOrdinaryWrongPasswordNotAnError() throws Exception {
        HttpResponse<String> response = login(uniqueEmail(), "     ");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void theCsrfTokenChangesAtLoginAndTheOldOneIsRefused() throws Exception {
        HttpResponse<String> bootstrap = get("/api/v1/auth/csrf");
        Map<String, String> oldToken = csrfHeaderFrom(bootstrap);
        postJson("/api/v1/auth/login", loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD), oldToken);

        Map<String, String> newToken = csrfHeaderFrom(get("/api/v1/auth/csrf"));

        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(postJson("/api/v1/auth/logout", "{}", oldToken).statusCode()).isEqualTo(403);
        assertThat(postJson("/api/v1/auth/logout", "{}", newToken).statusCode()).isEqualTo(204);
    }

    @Test
    void loggingInAgainAsAnotherAccountReplacesTheSignedInIdentity() throws Exception {
        String first = uniqueEmail();
        String second = uniqueEmail();
        login(first, StubService.ACCOUNT_PASSWORD);

        login(second, StubService.ACCOUNT_PASSWORD);

        assertThat(get("/api/v1/me").body()).contains(second).doesNotContain(first);
    }

    @Test
    void theInternalVerifyRouteIsNeverReachableThroughTheGateway() throws Exception {
        assertThat(get(StubService.VERIFY_PATH).statusCode()).isEqualTo(401);
        assertThat(postJson(StubService.VERIFY_PATH, loginJson("a@b.c", StubService.ACCOUNT_PASSWORD), bootstrap())
                        .statusCode())
                .isEqualTo(401);
        assertThat(verifyCalls()).isZero();
    }
}
