package com.faircourt.gateway;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Gateway tests for Account login: auth-service is the {@link StubService} (its verify route accepts one password). */
abstract class LoginTestBase extends GatewayTestBase {

    @DynamicPropertySource
    static void authServiceIsTheStub(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.auth-url", STUB::url);
        // Check the session on every request, so a test does not have to wait a minute.
        registry.add("faircourt.account.session-recheck-interval", () -> "0s");
    }

    static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    static String uniqueEmail() {
        return "user-" + java.util.UUID.randomUUID() + "@faircourt.local";
    }
}
