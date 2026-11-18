package com.faircourt.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The guest cookie on HTTPS (blueprint section 0; OWASP Session Management Cheat Sheet): with {@code COOKIE_SECURE=true}
 * and {@code GUEST_COOKIE_NAME=__Host-fc_guest} the cookie is Secure, has the {@code __Host-} name, and still has
 * HttpOnly, SameSite=Lax, Path=/ and no Domain (the {@code __Host-} prefix requires all of these).
 */
@TestPropertySource(properties = {"faircourt.guest.cookie-secure=true", "faircourt.guest.cookie-name=__Host-fc_guest"})
class GuestSecureCookieIT extends GuestTestBase {

    @Test
    void theCookieIsSecureAndUsesTheHostPrefixName() throws Exception {
        HttpResponse<String> response = checkIn(OPEN_CODE, guestJson("Mai"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        String cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-fc_guest="))
                .findFirst()
                .orElseThrow();
        assertThat(cookie)
                .containsIgnoringCase("; Secure")
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                .containsIgnoringCase("Path=/")
                .doesNotContainIgnoringCase("Domain=");
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("fc_guest="));
    }
}
