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

    private static IdentityToken at(String secret, Instant now) {
        return new IdentityToken(secret, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void aTokenVerifiesToTheAccountItWasIssuedFor() {
        String token = at(SECRET, T0).issue(USER);

        assertThat(at(SECRET, T0).verify(token)).contains(new CallerIdentity.Account(USER));
    }

    @Test
    void aTokenIsAcceptedFor30SecondsAndRefusedAfterwards() {
        String token = at(SECRET, T0).issue(USER);

        assertThat(at(SECRET, T0.plusSeconds(30)).verify(token)).isPresent();
        assertThat(at(SECRET, T0.plusSeconds(31)).verify(token)).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() {
        String token = at(SECRET, T0).issue(USER);

        assertThat(at(OTHER_SECRET, T0).verify(token)).isEmpty();
    }

    @Test
    void tamperedOrMalformedTokensAreRefusedWithoutAnException() {
        IdentityToken tokens = at(SECRET, T0);
        String token = tokens.issue(USER);
        String[] parts = token.split("\\.");

        assertThat(tokens.verify(parts[0] + "." + flip(parts[1]) + "." + parts[2]))
                .isEmpty();
        assertThat(tokens.verify(parts[0] + "." + parts[1] + "." + flip(parts[2])))
                .isEmpty();
        assertThat(tokens.verify("v2." + parts[1] + "." + parts[2])).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
        assertThat(tokens.verify("nodots")).isEmpty();
        assertThat(tokens.verify("a.b")).isEmpty();
        assertThat(tokens.verify("v1.!!!.???")).isEmpty();
        assertThat(tokens.verify("x".repeat(10_000))).isEmpty();
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
        String token = at(SECRET, T0.plusSeconds(10)).issue(USER);

        assertThat(at(SECRET, T0).verify(token)).isEmpty();
    }

    @Test
    void aSignedTokenWithALifetimeOtherThan30SecondsIsRefused() {
        long iat = T0.getEpochSecond();

        assertThat(at(SECRET, T0).verify(signed(iat, iat + 31))).isEmpty();
        assertThat(at(SECRET, T0).verify(signed(iat, iat + 29))).isEmpty();
        assertThat(at(SECRET, T0).verify(signed(iat, iat + 30))).isPresent();
    }

    private static String signed(long iat, long exp) {
        try {
            String claims =
                    "{\"kind\":\"ACCOUNT\",\"userId\":\"" + USER + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}";
            String body = "v1."
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
