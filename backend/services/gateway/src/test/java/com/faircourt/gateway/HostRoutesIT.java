package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.faircourt.common.identity.CallerIdentity;
import com.faircourt.common.identity.IdentityToken;
import com.faircourt.common.identity.IdentityTokenHeader;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Host board, gateway side (blueprint section 13; design 3.1, 3.4; AD-14). playmatch-service is the {@link StubService}.
 * Covers TC-07 to TC-11: only a signed-in account passes, the gateway adds one valid identity token for that account,
 * a client-sent token is dropped, and only host routes carry a token.
 */
class HostRoutesIT extends GuestTestBase {

    private static final String BOARD = "/api/v1/host/games/" + StubService.OPEN_GAME_ID + "/board";
    private static final String GAMES = "/api/v1/host/games";
    // The test-only secret set by backend/pom.xml for surefire and failsafe.
    private final IdentityToken tokens = new IdentityToken(System.getenv("INTERNAL_TOKEN_SECRET"), Clock.systemUTC());

    private void signIn(String email) throws Exception {
        HttpResponse<String> login =
                postJson("/api/v1/auth/login", loginJson(email, StubService.ACCOUNT_PASSWORD), bootstrap());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
    }

    private static void assertUnauthenticated(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"", "\"requestId\":\"");
    }

    private List<StubService.Received> hostCalls() {
        return STUB.received().stream()
                .filter(r -> r.path().startsWith("/api/v1/host/"))
                .toList();
    }

    @Test
    void anonymousCallersGet401AndNothingIsForwarded() throws Exception {
        assertUnauthenticated(get(BOARD));
        assertUnauthenticated(get(GAMES));

        assertThat(hostCalls()).isEmpty();
    }

    @Test
    void aGuestWithoutAnAccountGets401AndNothingIsForwardedOrAskedOfAuthService() throws Exception {
        HttpResponse<String> checkIn = checkIn(OPEN_CODE, guestJson("Mai"));
        STUB.knownGuestToken(tokenOf(guestCookieOf(checkIn)), UUID.randomUUID().toString());

        assertUnauthenticated(get(BOARD));
        assertUnauthenticated(get(GAMES));

        assertThat(STUB.received()).noneMatch(r -> r.path().startsWith("/api/v1/host/"));
        assertThat(STUB.received()).noneMatch(r -> r.path().equals(StubService.RESOLVE_PATH));
    }

    @Test
    void aSignedInAccountIsForwardedWithExactlyOneValidTokenAndNoCookie() throws Exception {
        signIn("host@faircourt.local");

        HttpResponse<String> response = get(BOARD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        StubService.Received call = hostCalls().getFirst();
        assertThat(call.path()).isEqualTo(BOARD);
        List<String> values = call.headers().get(IdentityTokenHeader.NAME);
        assertThat(values).hasSize(1);
        assertThat(tokens.verify(values.getFirst()))
                .contains(new CallerIdentity.Account(UUID.fromString(StubService.ACCOUNT_USER_ID)));
        assertThat(call.headers().containsKey("Cookie")).isFalse();
        assertThat(get(GAMES).statusCode()).isEqualTo(200);
    }

    @Test
    void aTokenSentByTheBrowserIsReplacedByTheGatewaysOwn() throws Exception {
        signIn("host@faircourt.local");
        UUID other = UUID.fromString(StubService.OTHER_ACCOUNT_USER_ID);
        String forged = tokens.issue(other);

        HttpResponse<String> response = send("GET", BOARD, Map.of(IdentityTokenHeader.NAME, forged));

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> values = hostCalls().getFirst().headers().get(IdentityTokenHeader.NAME);
        assertThat(values).hasSize(1);
        assertThat(tokens.verify(values.getFirst()))
                .contains(new CallerIdentity.Account(UUID.fromString(StubService.ACCOUNT_USER_ID)));
    }

    @Test
    void otherMethodsAndPathsAreNeverForwardedAndPublicRoutesCarryNoToken() throws Exception {
        signIn("host@faircourt.local");
        Map<String, String> csrf = bootstrap();

        for (String path : new String[] {BOARD, GAMES}) {
            for (String method : new String[] {"POST", "PUT", "DELETE"}) {
                assertThat(send(method, path, csrf).statusCode())
                        .as(method + " " + path)
                        .isIn(401, 403, 405);
            }
        }
        assertThat(get(BOARD + "/extra").statusCode()).isIn(401, 403, 404, 405);
        assertThat(hostCalls()).isEmpty();

        assertThat(get("/api/v1/public/games/FRI7Q2K9").statusCode()).isEqualTo(200);
        StubService.Received publicCall = STUB.received().stream()
                .filter(r -> r.path().startsWith("/api/v1/public/games/"))
                .findFirst()
                .orElseThrow();
        assertThat(publicCall.headers().containsKey(IdentityTokenHeader.NAME)).isFalse();
    }
}
