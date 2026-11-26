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
 * Signs and verifies the short-lived identity token the gateway sends to a service (AD-14; Host board design 3.4;
 * Attendance management design 3.2).
 *
 * <p>Format (v2): {@code v2.<base64url(claims JSON)>.<base64url(HMAC-SHA256(secret, "v2." + base64url(claims)))>} with
 * claims {@code {"kind":"ACCOUNT","userId":"uuid","aud":"service","method":"PATCH","path":"/api/v1/...","iat":<epoch
 * s>,"exp":<iat+30>}}. The token is bound to one target service, HTTP method and path (no query string), so a token seen
 * on the internal network cannot be replayed on another route. The JDK {@code Mac} is used; no new dependency. The
 * signature is compared in constant time and a token is refused after 30 seconds. The older v1 format is not accepted.
 */
public final class IdentityToken {

    /** Name of the property that holds the secret. */
    public static final String SECRET_PROPERTY = "faircourt.internal-token.secret";

    /** A token lives this long, in seconds. */
    public static final long LIFETIME_SECONDS = 30;

    static final int MIN_SECRET_BYTES = 32;
    private static final String VERSION = "v2";
    private static final String HMAC = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final String AUDIENCE_REGEX = "[a-z0-9-]{1,64}";
    private static final String METHOD_REGEX = "[A-Z]{3,10}";
    // Printable ASCII without the quote and the backslash, so the claims need no JSON escaping.
    private static final String PATH_REGEX = "/[!#-\\[\\]-~]{0,511}";
    private static final Pattern CLAIMS = Pattern.compile("\\{\"kind\":\"ACCOUNT\",\"userId\":\"([0-9a-f-]{36})\","
            + "\"aud\":\"(" + AUDIENCE_REGEX + ")\",\"method\":\"(" + METHOD_REGEX + ")\","
            + "\"path\":\"(" + PATH_REGEX + ")\",\"iat\":(\\d{1,12}),\"exp\":(\\d{1,12})\\}");

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

    /**
     * Issues a token for a signed-in account, valid for {@link #LIFETIME_SECONDS} seconds and only for the given target
     * service, HTTP method and path.
     *
     * @throws IllegalArgumentException when audience, method or path has a form the claims cannot carry
     */
    public String issue(UUID userId, String audience, String method, String path) {
        if (audience == null
                || !audience.matches(AUDIENCE_REGEX)
                || method == null
                || !method.matches(METHOD_REGEX)
                || path == null
                || !path.matches(PATH_REGEX)) {
            throw new IllegalArgumentException("Identity token audience, method or path has an unsupported form.");
        }
        long iat = clock.instant().getEpochSecond();
        String claims = "{\"kind\":\"ACCOUNT\",\"userId\":\"" + userId + "\",\"aud\":\"" + audience
                + "\",\"method\":\"" + method + "\",\"path\":\"" + path + "\",\"iat\":" + iat + ",\"exp\":"
                + (iat + LIFETIME_SECONDS) + "}";
        String body = VERSION + "." + base64(claims.getBytes(StandardCharsets.UTF_8));
        return body + "." + base64(sign(body));
    }

    /**
     * The caller named by a valid token, or empty for anything else (bad signature, version, claims, expiry, or a token
     * issued for another service, method or path).
     */
    public Optional<CallerIdentity.Account> verify(String token, String audience, String method, String path) {
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
            if (!m.group(2).equals(audience)
                    || !m.group(3).equals(method)
                    || !m.group(4).equals(path)) {
                return Optional.empty();
            }
            long iat = Long.parseLong(m.group(5));
            long exp = Long.parseLong(m.group(6));
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
