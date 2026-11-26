package com.faircourt.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class IdentityTokenTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final String OTHER_SECRET = "another-secret-another-secret-0123456789";
    private static final Instant T0 = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String AUD = "playmatch-service";
    private static final String GET = "GET";
    private static final String PATH = "/api/v1/host/games/g/board";

    private static IdentityToken at(String secret, Instant now) {
        return new IdentityToken(secret, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void aTokenVerifiesToTheAccountItWasIssuedFor() {
        String token = at(SECRET, T0).issue(USER, AUD, GET, PATH);

        assertThat(at(SECRET, T0).verify(token, AUD, GET, PATH)).contains(new CallerIdentity.Account(USER));
    }

    @Test
    void aTokenIsAcceptedFor30SecondsAndRefusedAfterwards() {
        String token = at(SECRET, T0).issue(USER, AUD, GET, PATH);

        assertThat(at(SECRET, T0.plusSeconds(30)).verify(token, AUD, GET, PATH)).isPresent();
        assertThat(at(SECRET, T0.plusSeconds(31)).verify(token, AUD, GET, PATH)).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() {
        String token = at(SECRET, T0).issue(USER, AUD, GET, PATH);

        assertThat(at(OTHER_SECRET, T0).verify(token, AUD, GET, PATH)).isEmpty();
    }

    @Test
    void tamperedOrMalformedTokensAreRefusedWithoutAnException() {
        IdentityToken tokens = at(SECRET, T0);
        String token = tokens.issue(USER, AUD, GET, PATH);
        String[] parts = token.split("\\.");

        assertThat(tokens.verify(parts[0] + "." + flip(parts[1]) + "." + parts[2], AUD, GET, PATH))
                .isEmpty();
        assertThat(tokens.verify(parts[0] + "." + parts[1] + "." + flip(parts[2]), AUD, GET, PATH))
                .isEmpty();
        assertThat(tokens.verify("v3." + parts[1] + "." + parts[2], AUD, GET, PATH))
                .isEmpty();
        assertThat(tokens.verify("", AUD, GET, PATH)).isEmpty();
        assertThat(tokens.verify(null, AUD, GET, PATH)).isEmpty();
        assertThat(tokens.verify("nodots", AUD, GET, PATH)).isEmpty();
        assertThat(tokens.verify("a.b", AUD, GET, PATH)).isEmpty();
        assertThat(tokens.verify("v2.!!!.???", AUD, GET, PATH)).isEmpty();
        assertThat(tokens.verify("x".repeat(10_000), AUD, GET, PATH)).isEmpty();
    }

    @Test
    void aTokenPassesOnlyForTheServiceMethodAndPathItWasIssuedFor() {
        IdentityToken tokens = at(SECRET, T0);
        String token = tokens.issue(USER, AUD, GET, PATH);

        assertThat(tokens.verify(token, AUD, GET, PATH)).isPresent();
        assertThat(tokens.verify(token, AUD, "PATCH", PATH)).isEmpty();
        assertThat(tokens.verify(token, AUD, GET, "/api/v1/host/games/g/attendance/a"))
                .isEmpty();
        assertThat(tokens.verify(token, "player-service", GET, PATH)).isEmpty();
        assertThat(at(SECRET, T0.plusSeconds(31)).verify(token, AUD, GET, PATH)).isEmpty();
    }

    @Test
    void aValidlySignedV1TokenIsRefused() {
        long iat = T0.getEpochSecond();
        String claims =
                "{\"kind\":\"ACCOUNT\",\"userId\":\"" + USER + "\",\"iat\":" + iat + ",\"exp\":" + (iat + 30) + "}";

        assertThat(at(SECRET, T0).verify(sign("v1", claims), AUD, GET, PATH)).isEmpty();
    }

    @Test
    void issuingRefusesAudienceMethodOrPathTheClaimsCannotCarry() {
        IdentityToken tokens = at(SECRET, T0);

        assertThatThrownBy(() -> tokens.issue(USER, "Bad\"aud", GET, PATH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue(USER, AUD, "get", PATH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue(USER, AUD, GET, "/a\"b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokens.issue(USER, AUD, GET, "no-slash")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSecretShorterThan32BytesOrMissingStopsStartupAndNamesOnlyTheProperty() {
        String shortSecret = "s".repeat(31);

        assertThatThrownBy(() -> at(shortSecret, T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(IdentityToken.SECRET_PROPERTY)
                .hasMessageNotContaining(shortSecret);
        assertThatThrownBy(() -> at(null, T0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(IdentityToken.SECRET_PROPERTY);
        assertThat(at("s".repeat(32), T0)).isNotNull();
    }

    @Test
    void aSignedTokenIssuedInTheFutureIsRefused() {
        String token = at(SECRET, T0.plusSeconds(10)).issue(USER, AUD, GET, PATH);

        assertThat(at(SECRET, T0).verify(token, AUD, GET, PATH)).isEmpty();
    }

    @Test
    void aSignedTokenWithALifetimeOtherThan30SecondsIsRefused() {
        long iat = T0.getEpochSecond();

        assertThat(at(SECRET, T0).verify(signed(iat, iat + 31), AUD, GET, PATH)).isEmpty();
        assertThat(at(SECRET, T0).verify(signed(iat, iat + 29), AUD, GET, PATH)).isEmpty();
        assertThat(at(SECRET, T0).verify(signed(iat, iat + 30), AUD, GET, PATH)).isPresent();
    }

    private static String signed(long iat, long exp) {
        return sign(
                "v2",
                "{\"kind\":\"ACCOUNT\",\"userId\":\"" + USER + "\",\"aud\":\"" + AUD + "\",\"method\":\"" + GET
                        + "\",\"path\":\"" + PATH + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}");
    }

    private static String sign(String version, String claims) {
        try {
            String body = version + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String flip(String part) {
        char first = part.charAt(0);
        return (first == 'A' ? 'B' : 'A') + part.substring(1);
    }
}
