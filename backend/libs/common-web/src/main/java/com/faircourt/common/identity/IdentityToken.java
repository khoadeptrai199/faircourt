package com.faircourt.common.identity;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs and verifies the short-lived identity token the gateway sends to a service (AD-14; Host board design 3.4).
 *
 * <p>Format: {@code v1.<base64url(claims JSON)>.<base64url(HMAC-SHA256(secret, "v1." + base64url(claims)))>} with
 * claims {@code {"kind":"ACCOUNT","userId":"uuid","iat":<epoch s>,"exp":<iat+30>}}. The JDK {@code Mac} is used; no new
 * dependency. The signature is compared in constant time and a token is refused after 30 seconds.
 */
public final class IdentityToken {

    /** Name of the property that holds the secret. */
    public static final String SECRET_PROPERTY = "faircourt.internal-token.secret";

    /** A token lives this long, in seconds. */
    public static final long LIFETIME_SECONDS = 30;

    static final int MIN_SECRET_BYTES = 32;
    private static final String VERSION = "v1";
    private static final String HMAC = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 1024;
    private static final Pattern CLAIMS = Pattern.compile(
            "\\{\"kind\":\"ACCOUNT\",\"userId\":\"([0-9a-f-]{36})\",\"iat\":(\\d{1,12}),\"exp\":(\\d{1,12})\\}");

    private final byte[] secret;
    private final Clock clock;

    /** @throws IllegalStateException when the secret is missing or shorter than 32 bytes (the value is never named) */
    public IdentityToken(String secret, Clock clock) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("The property " + SECRET_PROPERTY
                    + " (env INTERNAL_TOKEN_SECRET) must be set to at least " + MIN_SECRET_BYTES + " bytes.");
        }
        this.secret = bytes;
        this.clock = clock;
    }

    /** Issues a token for a signed-in account, valid for {@link #LIFETIME_SECONDS} seconds. */
    public String issue(UUID userId) {
        long iat = clock.instant().getEpochSecond();
        String claims = "{\"kind\":\"ACCOUNT\",\"userId\":\"" + userId + "\",\"iat\":" + iat + ",\"exp\":"
                + (iat + LIFETIME_SECONDS) + "}";
        String body = VERSION + "." + base64(claims.getBytes(StandardCharsets.UTF_8));
        return body + "." + base64(sign(body));
    }

    /** The caller named by a valid token, or empty for anything else (bad signature, version, claims, expiry). */
    public Optional<CallerIdentity.Account> verify(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            byte[] given = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(given, expected)) {
                return Optional.empty();
            }
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Matcher m = CLAIMS.matcher(claims);
            if (!m.matches()) {
                return Optional.empty();
            }
            long iat = Long.parseLong(m.group(2));
            long exp = Long.parseLong(m.group(3));
            long now = clock.instant().getEpochSecond();
            if (exp - iat != LIFETIME_SECONDS || now > exp || now < iat) {
                return Optional.empty();
            }
            return Optional.of(new CallerIdentity.Account(UUID.fromString(m.group(1))));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String text) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
