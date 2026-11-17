package com.faircourt.gateway.csrf;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public CSRF bootstrap (blueprint section 0): the browser app calls it once, then sends {@code token} in the header
 * named {@code headerName} on every write. The token is stored in the gateway's HTTP session (HttpOnly cookie), so
 * JavaScript never reads a cookie.
 */
@RestController
public class CsrfController {

    @GetMapping("/api/v1/auth/csrf")
    CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
    }
}
