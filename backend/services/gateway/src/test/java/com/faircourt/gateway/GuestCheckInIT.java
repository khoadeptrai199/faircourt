package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Guest check-in, gateway side (blueprint sections 12, 13; Guest check-in design sections 3, 8; OWASP Session
 * Management and CSRF Cheat Sheets). playmatch-service and auth-service are stubs. Covers: the cookie and its flags,
 * the two-field answer, same browser again, ignored cookies, CSRF, JSON only, input limits, error mapping, the three
 * caller kinds, and that logout no longer clears the guest cookie.
 */
@ExtendWith(OutputCaptureExtension.class)
class GuestCheckInIT extends GuestTestBase {

    private static final String PENDING_BODY =
            "{\"attendanceState\":\"PENDING\",\"message\":\"Waiting for host approval\"}";

    @Test
    void aNewGuestGets201TheTwoFieldAnswerAndAHardenedCookie() throws Exception {
        HttpResponse<String> response = checkIn(OPEN_CODE, guestJson("Mai"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(response.body()).isEqualTo(PENDING_BODY);
        JsonAllowlist.assertOnlyFields(response.body(), "attendanceState", "message");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        String cookie = guestCookieOf(response);
        assertThat(tokenOf(cookie)).matches("[A-Za-z0-9_-]{43}");
        assertThat(cookie)
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                .containsIgnoringCase("Path=/")
                .doesNotContainIgnoringCase("Domain=")
                .doesNotContainIgnoringCase("Secure");
        // COOKIE_SECURE defaults to false (plain local HTTP, blueprint section 0); the true case is
        // GuestSecureCookieIT.
        Matcher maxAge = Pattern.compile("Max-Age=(\\d+)").matcher(cookie);
        assertThat(maxAge.find()).isTrue();
        // The stub's credential ends 14 hours from now.
        assertThat(Long.parseLong(maxAge.group(1))).isBetween(14L * 3600 - 60, 14L * 3600);
        // The token is only in the cookie, never in the body.
        assertThat(response.body()).doesNotContain(tokenOf(cookie));
    }

    @Test
    void thePlaymatchRequestCarriesNoBrowserCookieAndAnExplicitNullExistingAttendance() throws Exception {
        checkIn(OPEN_CODE, guestJson("Mai"));

        StubService.Received call = STUB.received().stream()
                .filter(r -> r.path().endsWith("/guest-check-ins"))
                .findFirst()
                .orElseThrow();
        assertThat(call.path()).isEqualTo("/internal/v1/games/by-code/OPENGAME/guest-check-ins");
        assertThat(call.headers().getFirst("Cookie")).isNull();
        assertThat(call.headers().getFirst("X-Request-Id")).isNotBlank();
        assertThat(call.body()).contains("\"displayName\":\"Mai\"").contains("\"existingAttendanceId\":null");
    }

    @Test
    void theSameBrowserAgainGets200TheCurrentStateNoNewCookieAndTheExistingAttendanceIsPassedOn() throws Exception {
        HttpResponse<String> first = checkIn(OPEN_CODE, guestJson("Mai"));
        String attendanceId = UUID.randomUUID().toString();
        STUB.knownGuestToken(tokenOf(guestCookieOf(first)), attendanceId);

        HttpResponse<String> second = checkIn(OPEN_CODE, guestJson("Mai"));

        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat(second.body()).isEqualTo(PENDING_BODY);
        assertThat(second.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("fc_guest="));
        List<StubService.Received> calls = STUB.received().stream()
                .filter(r -> r.path().endsWith("/guest-check-ins"))
                .toList();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(1).body()).contains("\"existingAttendanceId\":\"" + attendanceId + "\"");
        assertThat(calls.get(1).headers().getFirst("Cookie")).isNull();
    }

    @Test
    void anUnknownExpiredOrRevokedCookieIsIgnoredAndTheGuestChecksInAsNewWithANewCookie() throws Exception {
        String oldToken = "9".repeat(43);

        HttpResponse<String> response = postJson(
                "/api/v1/public/games/" + OPEN_CODE + "/guests",
                guestJson("Mai"),
                withCookie(bootstrap(), "fc_guest=" + oldToken));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(tokenOf(guestCookieOf(response))).isNotEqualTo(oldToken);
        StubService.Received call = STUB.received().stream()
                .filter(r -> r.path().endsWith("/guest-check-ins"))
                .findFirst()
                .orElseThrow();
        assertThat(call.body()).contains("\"existingAttendanceId\":null");
    }

    private static Map<String, String> withCookie(Map<String, String> headers, String cookie) {
        Map<String, String> all = new HashMap<>(headers);
        all.put("Cookie", cookie);
        return all;
    }

    @Test
    void anotherBrowserIsAnotherGuestEvenWithTheSameName() throws Exception {
        assertThat(checkIn(OPEN_CODE, guestJson("Mai")).statusCode()).isEqualTo(201);
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        assertThat(checkIn(OPEN_CODE, guestJson("Mai")).statusCode()).isEqualTo(201);

        assertThat(checkInCalls()).isEqualTo(2);
    }

    @Test
    void aLowercaseCodeIsNormalisedBeforeItIsForwarded() throws Exception {
        assertThat(checkIn("opengame", guestJson("Mai")).statusCode()).isEqualTo(201);

        assertThat(STUB.received())
                .anyMatch(r -> r.path().equals("/internal/v1/games/by-code/OPENGAME/guest-check-ins"));
    }

    @Test
    void aMissingCsrfTokenIs403AndNothingIsForwarded() throws Exception {
        HttpResponse<String> response =
                postJson("/api/v1/public/games/" + OPEN_CODE + "/guests", guestJson("Mai"), Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("fc_guest="));
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aWrongCsrfTokenIs403() throws Exception {
        bootstrap();

        HttpResponse<String> response = postJson(
                "/api/v1/public/games/" + OPEN_CODE + "/guests", guestJson("Mai"), Map.of("X-CSRF-TOKEN", "wrong"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void onlyJsonIsAcceptedAndNothingIsForwardedOtherwise() throws Exception {
        HttpRequest.Builder form = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/public/games/" + OPEN_CODE + "/guests"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("displayName=Mai"));
        bootstrap().forEach(form::header);

        HttpResponse<String> response = browser.send(form.build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(400, 415);
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aBodyOverFourKilobytesIsRefusedBeforeItIsForwarded() throws Exception {
        // A valid name and valid answers, padded with whitespace past 4096 bytes: only the size cap can refuse it.
        String padded = guestJson("Mai") + " ".repeat(5_000);
        assertThat(padded.length()).isGreaterThan(4096);

        HttpResponse<String> response = checkIn(OPEN_CODE, padded);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void neitherTheNameNorTheAnswersNorTheTokenAppearInTheLogAfterASuccessOrAFailure(CapturedOutput output)
            throws Exception {
        HttpResponse<String> ok = checkIn(OPEN_CODE, guestJson("Zorbulon"));
        checkIn(StubService.FAILING_GAME_CODE, guestJson("Quuxmire"));
        checkIn(OPEN_CODE, guestJson("Frob\\u0007nitz"));

        assertThat(output.getAll())
                .doesNotContain("Zorbulon", "Quuxmire", "Frob", "positioning", tokenOf(guestCookieOf(ok)));
    }

    @Test
    void badNamesAre400AndNeverForwarded() throws Exception {
        for (String name : new String[] {"", "    ", "A".repeat(81), "Ma\\u0007i", "Ma\\ni", "\\t"}) {
            HttpResponse<String> response = checkIn(OPEN_CODE, guestJson(name));

            assertThat(response.statusCode()).as(name).isEqualTo(400);
            assertThat(response.body()).contains("\"code\":\"VALIDATION_FAILED\"");
        }
        for (String json : new String[] {"{}", "{not json", "{\"displayName\":null}", "[]"}) {
            assertThat(checkIn(OPEN_CODE, json).statusCode()).as(json).isEqualTo(400);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aNameOfEightyCharactersIsAcceptedAndASpacePaddedNameIsTrimmed() throws Exception {
        assertThat(checkIn(OPEN_CODE, guestJson("A".repeat(80))).statusCode()).isEqualTo(201);
        assertThat(checkIn(OPEN_CODE, guestJson("   Mai   ")).statusCode()).isEqualTo(201);

        assertThat(STUB.received().stream().map(StubService.Received::body))
                .anyMatch(b -> b.contains("\"displayName\":\"Mai\""));
    }

    @Test
    void partialOrOutOfRangeAnswersAre400AndNeverForwarded() throws Exception {
        String[] answers = {
            "{\"serve\":1,\"rally\":1,\"movement\":1,\"positioning\":null}",
            "{\"serve\":1}",
            "{\"serve\":4,\"rally\":0,\"movement\":0,\"positioning\":0}",
            "{\"serve\":-1,\"rally\":0,\"movement\":0,\"positioning\":0}",
            "{\"serve\":\"x\",\"rally\":0,\"movement\":0,\"positioning\":0}"
        };
        for (String a : answers) {
            HttpResponse<String> response = checkIn(OPEN_CODE, "{\"displayName\":\"Mai\",\"answers\":" + a + "}");

            assertThat(response.statusCode()).as(a).isEqualTo(400);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void unknownAnswersAreAccepted() throws Exception {
        String allNull = "{\"serve\":null,\"rally\":null,\"movement\":null,\"positioning\":null}";

        assertThat(checkIn(OPEN_CODE, "{\"displayName\":\"Mai\",\"answers\":null}")
                        .statusCode())
                .isEqualTo(201);
        assertThat(checkIn(OPEN_CODE, "{\"displayName\":\"Mai\"}").statusCode()).isEqualTo(201);
        assertThat(checkIn(OPEN_CODE, "{\"displayName\":\"Mai\",\"answers\":" + allNull + "}")
                        .statusCode())
                .isEqualTo(201);
    }

    @Test
    void errorsOfPlaymatchAreMappedAndSetNoCookie() throws Exception {
        HttpResponse<String> unknown = checkIn(StubService.UNKNOWN_GAME_CODE, guestJson("Mai"));
        HttpResponse<String> closed = checkIn(StubService.CLOSED_GAME_CODE, guestJson("Mai"));
        HttpResponse<String> failing = checkIn(StubService.FAILING_GAME_CODE, guestJson("Mai"));
        HttpResponse<String> malformed = checkIn("BAD-CODE", guestJson("Mai"));

        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(closed.statusCode()).isEqualTo(409);
        assertThat(closed.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(failing.statusCode()).isEqualTo(503);
        assertThat(failing.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        assertThat(malformed.statusCode()).isEqualTo(404);
        for (HttpResponse<String> response : List.of(unknown, closed, failing, malformed)) {
            assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("fc_guest="));
        }
        // The malformed code never reaches playmatch-service.
        assertThat(STUB.received()).noneMatch(r -> r.path().contains("BAD-CODE"));
    }

    @Test
    void whenAuthServiceCannotResolveTheCookieTheAnswerIs503AndPlaymatchIsNotCalled() throws Exception {
        STUB.resolveFails(true);

        HttpResponse<String> response = postJson(
                "/api/v1/public/games/" + OPEN_CODE + "/guests",
                guestJson("Mai"),
                withCookie(bootstrap(), "fc_guest=" + "8".repeat(43)));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(checkInCalls()).isZero();
    }

    @Test
    void aSignedInAccountMayAlsoCheckInAsAGuestAndTheAccountStaysSignedIn() throws Exception {
        HttpResponse<String> login =
                postJson("/api/v1/auth/login", loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD), bootstrap());
        assertThat(login.statusCode()).isEqualTo(200);

        HttpResponse<String> response = checkIn(OPEN_CODE, guestJson("Mai"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(200);
    }

    @Test
    void onlyPostIsAllowedOnTheRoute() throws Exception {
        String path = "/api/v1/public/games/" + OPEN_CODE + "/guests";
        Map<String, String> csrf = bootstrap();

        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, path, csrf).statusCode()).as(method).isEqualTo(401);
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void logoutEndsTheAccountSessionButKeepsTheGuestCookie() throws Exception {
        postJson("/api/v1/auth/login", loginJson(uniqueEmail(), StubService.ACCOUNT_PASSWORD), bootstrap());
        HttpResponse<String> first = checkIn(OPEN_CODE, guestJson("Mai"));
        STUB.knownGuestToken(tokenOf(guestCookieOf(first)), UUID.randomUUID().toString());

        HttpResponse<String> logout = postJson("/api/v1/auth/logout", "{}", bootstrap());

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().firstValue("Clear-Site-Data")).hasValue("\"cache\"");
        List<String> cookies = logout.headers().allValues("Set-Cookie");
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst()).startsWith("fc_sid=").containsIgnoringCase("Max-Age=0");
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
        // The guest cookie is still in the browser: checking in again is recognised as the same guest.
        HttpResponse<String> again = checkIn(OPEN_CODE, guestJson("Mai"));
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(STUB.received()).anyMatch(r -> r.path().equals(StubService.RESOLVE_PATH));
    }
}
