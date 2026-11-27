package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.testsupport.JsonAllowlist;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Member join and "my game", gateway side (blueprint sections 3, 12, 13; My attendance design sections 3 and 8).
 * playmatch-service and auth-service are stubs. Covers TC-12 to TC-18 and the gateway part of TC-20: the two answers and
 * their keys, no-store, 401 kinds, the guest cookie of another game, account over guest cookie, the member display name,
 * and that the browser cookie and identity-like headers never reach playmatch-service.
 */
@ExtendWith(OutputCaptureExtension.class)
class PlayerAccessIT extends GuestTestBase {

    private static final String GAME = StubService.OPEN_GAME_ID;

    private String signIn(String email) throws Exception {
        HttpResponse<String> login =
                postJson("/api/v1/auth/login", loginJson(email, StubService.ACCOUNT_PASSWORD), bootstrap());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return email;
    }

    private HttpResponse<String> join(String gameId) throws Exception {
        return send("POST", "/api/v1/games/" + gameId + "/join", bootstrap());
    }

    private HttpResponse<String> me(String gameId) throws Exception {
        return get("/api/v1/games/" + gameId + "/me");
    }

    private HttpResponse<String> meWithCookies(String gameId, String cookie) throws Exception {
        return send("GET", "/api/v1/games/" + gameId + "/me", Map.of("Cookie", cookie));
    }

    /** Checks in a guest and registers its token with the auth stub; returns the cookie header value. */
    private String guestCookie(UUID attendanceId) throws Exception {
        HttpResponse<String> response = checkIn(OPEN_CODE, guestJson("Mai"));
        String token = tokenOf(guestCookieOf(response));
        STUB.knownGuestToken(token, attendanceId.toString());
        return "fc_guest=" + token;
    }

    private List<StubService.Received> callsTo(String suffix) {
        return STUB.received().stream().filter(r -> r.path().endsWith(suffix)).toList();
    }

    @Test
    void aMemberJoinsFirstWith201ThenWith200AndTheAnswerHasOnlyTheState() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> first = join(GAME);
        HttpResponse<String> second = join(GAME);

        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        assertThat(first.body()).isEqualTo("{\"attendanceState\":\"READY\"}");
        JsonAllowlist.assertOnlyFields(first.body(), "attendanceState");
        assertThat(first.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
        assertThat(second.body()).isEqualTo("{\"attendanceState\":\"READY\"}");
        assertThat(second.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
    }

    @Test
    void theJoinRequestCarriesTheSessionUserIdAndTheNameBeforeTheAtSignAndNoBrowserCookie() throws Exception {
        signIn("host@faircourt.local");

        join(GAME);

        StubService.Received call = callsTo("/member-attendances").getFirst();
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.path()).isEqualTo("/internal/v1/games/" + GAME + "/member-attendances");
        assertThat(call.body())
                .contains("\"userId\":\"" + StubService.ACCOUNT_USER_ID + "\"")
                .contains("\"displayName\":\"host\"")
                .doesNotContain("faircourt.local");
        assertThat(call.headers().getFirst("Cookie")).isNull();
        assertThat(call.headers().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void theMemberNameIsTheEmailPartBeforeTheAtSignAtMostEightyCharactersWithoutControlCharacters() throws Exception {
        signIn("A".repeat(100) + "@faircourt.local");

        join(GAME);

        assertThat(callsTo("/member-attendances").getFirst().body())
                .contains("\"displayName\":\"" + "A".repeat(80) + "\"");
    }

    @Test
    void theMemberNameIsCutByCodePointsSoASurrogatePairIsNeverSplit() throws Exception {
        String emoji = "😀";
        // 79 letters + 3 emoji: a cut by UTF-16 units at 80 would keep half of the first emoji.
        signIn("a".repeat(79) + emoji.repeat(3) + "@faircourt.local");

        join(GAME);

        String body = callsTo("/member-attendances").getFirst().body();
        assertThat(body).contains("\"displayName\":\"" + "a".repeat(79) + emoji + "\"");
        assertThat(body).doesNotContain(emoji + emoji);
    }

    @Test
    void anEmailWithOnlyControlCharactersBeforeTheAtSignGivesAnEmptyNameLeftToPlaymatchToRefuse() throws Exception {
        signIn("\\u0007@faircourt.local");

        join(GAME);

        // Known limit: the gateway has no fallback copy; playmatch-service answers 400 for an empty name.
        assertThat(callsTo("/member-attendances").getFirst().body()).contains("\"displayName\":\"\"");
    }

    @Test
    void aSecondAccountNeverReceivesTheRowOfTheFirstAccount() throws Exception {
        signIn("host@faircourt.local");
        join(GAME);
        assertThat(me(GAME).statusCode()).isEqualTo(200);
        // A second browser with its own session, signed in as a different account that has not joined.
        browser = java.net.http.HttpClient.newBuilder()
                .cookieHandler(new java.net.CookieManager())
                .build();
        signIn(StubService.OTHER_EMAIL_PREFIX + "b@faircourt.local");

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("READY", "attendanceState");
        List<StubService.Received> calls = callsTo("/my-attendance");
        assertThat(calls.getLast().query()).isEqualTo("userId=" + StubService.OTHER_ACCOUNT_USER_ID);
        assertThat(calls.getLast().query()).doesNotContain(StubService.ACCOUNT_USER_ID);
    }

    @Test
    void joinWithoutCredentialsIs401UnauthenticatedAndNothingIsForwarded() throws Exception {
        HttpResponse<String> response = join(GAME);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void aGuestCookieCannotJoinAsAMember() throws Exception {
        guestCookie(UUID.randomUUID());

        HttpResponse<String> response = join(GAME);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(callsTo("/member-attendances")).isEmpty();
    }

    @Test
    void joinWithoutACsrfTokenIs403AndNothingIsForwarded() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> response = send("POST", "/api/v1/games/" + GAME + "/join", Map.of());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(STUB.received().stream().filter(r -> r.path().contains("/member-attendances")))
                .isEmpty();
    }

    @Test
    void aUserIdInTheJoinBodyIsIgnored() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> response =
                postJson("/api/v1/games/" + GAME + "/join", "{\"userId\":\"" + UUID.randomUUID() + "\"}", bootstrap());

        assertThat(response.statusCode()).isEqualTo(201);
        // A userId in the body is ignored: the session decides who joins.
        assertThat(callsTo("/member-attendances").getFirst().body())
                .contains("\"userId\":\"" + StubService.ACCOUNT_USER_ID + "\"");
    }

    @Test
    void joinErrorsAreMapped404ForUnknownOrMalformed409ForClosed503ForADownService() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> unknown = join(StubService.UNKNOWN_GAME_ID);
        HttpResponse<String> malformed = join("not-a-uuid");
        HttpResponse<String> closed = join(StubService.CLOSED_GAME_ID);
        HttpResponse<String> failing = join(StubService.FAILING_GAME_ID);

        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(malformed.statusCode()).isEqualTo(404);
        assertThat(closed.statusCode()).isEqualTo(409);
        assertThat(closed.body()).contains("\"code\":\"STATE_CONFLICT\"");
        assertThat(failing.statusCode()).isEqualTo(503);
        assertThat(failing.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
        // The malformed id never reaches playmatch-service.
        assertThat(STUB.received()).noneMatch(r -> r.path().contains("not-a-uuid"));
    }

    @Test
    void aMemberReadsOwnReadyStateWithTheTwoKeysAndNoStore() throws Exception {
        signIn("host@faircourt.local");
        join(GAME);

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"attendanceState\":\"READY\",\"assignment\":null}");
        JsonAllowlist.assertOnlyFields(response.body(), "attendanceState", "assignment");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        StubService.Received call = callsTo("/my-attendance").getFirst();
        assertThat(call.path()).isEqualTo("/internal/v1/games/" + GAME + "/my-attendance");
        assertThat(call.headers().getFirst("Cookie")).isNull();
    }

    @Test
    void aMemberReadsOwnAssignmentExactlyAsPlaymatchAnsweredAndNothingElse() throws Exception {
        signIn("host@faircourt.local");
        join(GAME);
        String assignment = "{\"matchNo\":3,\"courtNo\":2,\"matchState\":\"PLANNED\"}";
        STUB.myAttendanceAssignment(assignment);

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"attendanceState\":\"READY\",\"assignment\":" + assignment + "}");
        JsonAllowlist.assertOnlyFields(
                response.body(),
                "attendanceState",
                "assignment",
                "assignment.matchNo",
                "assignment.courtNo",
                "assignment.matchState");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(v -> assertThat(v).contains("no-store"));
    }

    @Test
    void anExtraKeyInsideTheAssignmentIsDroppedAndAFinishedZeroOrUnreadableAssignmentIs503() throws Exception {
        signIn("host@faircourt.local");
        join(GAME);
        STUB.myAttendanceAssignment("{\"matchNo\":3,\"courtNo\":2,\"matchState\":\"ACTIVE\",\"team\":\"A\"}");

        HttpResponse<String> extra = me(GAME);
        STUB.myAttendanceAssignment("{\"matchNo\":3,\"courtNo\":2,\"matchState\":\"FINISHED\"}");
        HttpResponse<String> finished = me(GAME);
        STUB.myAttendanceAssignment("{\"matchNo\":0,\"courtNo\":2,\"matchState\":\"PLANNED\"}");
        HttpResponse<String> zero = me(GAME);
        STUB.myAttendanceAssignment("{\"matchNo\":\"x\",\"courtNo\":2,\"matchState\":\"PLANNED\"}");
        HttpResponse<String> unreadable = me(GAME);

        assertThat(extra.statusCode()).as(extra.body()).isEqualTo(200);
        assertThat(extra.body())
                .isEqualTo("{\"attendanceState\":\"READY\",\"assignment\":"
                        + "{\"matchNo\":3,\"courtNo\":2,\"matchState\":\"ACTIVE\"}}")
                .doesNotContain("team");
        for (HttpResponse<String> bad : List.of(finished, zero, unreadable)) {
            assertThat(bad.statusCode()).as(bad.body()).isEqualTo(503);
            assertThat(bad.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain("assignment");
        }
    }

    @Test
    void aGuestOnlyEverAsksForItsOwnAttendanceAndAnonymousOrUnknownCallersGetNoAssignment() throws Exception {
        UUID attendanceA = UUID.randomUUID();
        UUID attendanceB = UUID.randomUUID();
        STUB.knownGuestToken("a".repeat(43), attendanceA.toString());
        STUB.knownGuestToken("b".repeat(43), attendanceB.toString());
        String cookieB = "fc_guest=" + "b".repeat(43);
        STUB.myAttendanceAssignment("{\"matchNo\":1,\"courtNo\":1,\"matchState\":\"PLANNED\"}");

        HttpResponse<String> asB = meWithCookies(StubService.GUEST_GAME_ID, cookieB);
        HttpResponse<String> anonymous = me(StubService.GUEST_GAME_ID);

        assertThat(asB.statusCode()).as(asB.body()).isEqualTo(200);
        StubService.Received call = callsTo("/my-attendance").getFirst();
        assertThat(call.query()).isEqualTo("attendanceId=" + attendanceB);
        assertThat(callsTo("/my-attendance")).hasSize(1);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).contains("\"code\":\"UNAUTHENTICATED\"").doesNotContain("assignment");
    }

    @Test
    void anAccountWithNoRowInTheGameGets404WithNoAssignmentEvenWhenAMatchExists() throws Exception {
        signIn(StubService.OTHER_EMAIL_PREFIX + "b@faircourt.local");
        STUB.myAttendanceAssignment("{\"matchNo\":1,\"courtNo\":1,\"matchState\":\"PLANNED\"}");

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("assignment");
    }

    @Test
    void aGuestReadsOwnPendingState() throws Exception {
        guestCookie(UUID.randomUUID());

        HttpResponse<String> response = me(StubService.GUEST_GAME_ID);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"attendanceState\":\"PENDING\",\"assignment\":null}");
        JsonAllowlist.assertOnlyFields(response.body(), "attendanceState", "assignment");
        StubService.Received call = callsTo("/my-attendance").getFirst();
        assertThat(call.headers().getFirst("Cookie")).isNull();
    }

    @Test
    void withoutAnyCredentialMeIs401UnauthenticatedEvenWithIdentityLikeHeaders() throws Exception {
        HttpResponse<String> plain = me(GAME);
        HttpResponse<String> spoofed = send(
                "GET",
                "/api/v1/games/" + GAME + "/me",
                Map.of(
                        "X-Internal-User-Id", StubService.ACCOUNT_USER_ID,
                        "X-User-Id", StubService.ACCOUNT_USER_ID,
                        "X-Attendance-Id", UUID.randomUUID().toString()));

        for (HttpResponse<String> response : List.of(plain, spoofed)) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        }
        assertThat(STUB.received()).isEmpty();
    }

    @Test
    void anUnknownExpiredOrRevokedGuestCookieIs401GuestAccessExpiredAndNothingIsRead() throws Exception {
        HttpResponse<String> response = meWithCookies(GAME, "fc_guest=" + "7".repeat(43));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"GUEST_ACCESS_EXPIRED\"");
        assertThat(callsTo("/my-attendance")).isEmpty();
    }

    @Test
    void aGuestCookieOfAnotherGameIs404WithNoDataOfTheOtherGame() throws Exception {
        guestCookie(UUID.randomUUID());

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"").doesNotContain("PENDING", "attendanceState");
    }

    @Test
    void anAccountThatHasNotJoinedThisGameIs404() throws Exception {
        signIn("host@faircourt.local");
        join(GAME);

        HttpResponse<String> response = me(StubService.CLOSED_GAME_ID);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void aMalformedGameIdIs404AfterTheIdentityCheck() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> response = me("not-a-uuid");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(callsTo("/my-attendance")).isEmpty();
    }

    @Test
    void anAccountWinsOverAGuestCookieAndThePlaymatchRequestAsksForTheUserNotTheAttendance() throws Exception {
        // The browser holds both a guest cookie (known to the auth stub) and an account session.
        guestCookie(UUID.randomUUID());
        signIn("host@faircourt.local");
        join(GAME);

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("READY");
        List<StubService.Received> calls = callsTo("/my-attendance");
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().query()).isEqualTo("userId=" + StubService.ACCOUNT_USER_ID);
        assertThat(STUB.received()).noneMatch(r -> r.path().equals(StubService.RESOLVE_PATH));
    }

    @Test
    void whenPlaymatchFailsMeIs503() throws Exception {
        signIn("host@faircourt.local");
        STUB.myAttendanceFails(true);

        HttpResponse<String> response = me(GAME);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    void whenAuthServiceCannotResolveTheGuestCookieMeIs503() throws Exception {
        STUB.resolveFails(true);

        HttpResponse<String> response = meWithCookies(GAME, "fc_guest=" + "6".repeat(43));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(callsTo("/my-attendance")).isEmpty();
    }

    @Test
    void onlyPostIsAllowedOnJoinAndOnlyGetOnMe() throws Exception {
        signIn("host@faircourt.local");
        Map<String, String> csrf = bootstrap();

        for (String method : new String[] {"GET", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, "/api/v1/games/" + GAME + "/join", csrf).statusCode())
                    .as("join " + method)
                    .isIn(401, 403, 404, 405);
        }
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            assertThat(send(method, "/api/v1/games/" + GAME + "/me", csrf).statusCode())
                    .as("me " + method)
                    .isIn(401, 403, 404, 405);
        }
        assertThat(callsTo("/member-attendances")).isEmpty();
        assertThat(callsTo("/my-attendance")).isEmpty();
    }

    @Test
    void neitherTheEmailNorTheNameNorTheGuestTokenAppearInTheLog(CapturedOutput output) throws Exception {
        signIn("zorbulon.secret@faircourt.local");
        join(GAME);
        me(GAME);
        join(StubService.FAILING_GAME_ID);
        String cookie = guestCookie(UUID.randomUUID());
        me(StubService.GUEST_GAME_ID);

        assertThat(output.getAll())
                .doesNotContain("zorbulon", cookie.substring("fc_guest=".length()), StubService.ACCOUNT_PASSWORD);
    }
}
