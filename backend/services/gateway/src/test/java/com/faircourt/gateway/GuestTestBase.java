package com.faircourt.gateway;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gateway tests for Guest check-in: playmatch-service and auth-service are the {@link StubService} (its check-in route
 * answers by game code, its resolve route knows the tokens a test registers). Account login is available too, so a
 * test can check the route as an anonymous caller, a signed-in account and a guest.
 */
abstract class GuestTestBase extends LoginTestBase {

    static final String OPEN_CODE = "OPENGAME";

    @DynamicPropertySource
    static void playmatchIsTheStub(DynamicPropertyRegistry registry) {
        registry.add("faircourt.services.playmatch-url", STUB::url);
    }

    static String guestJson(String name) {
        return "{\"displayName\":\"" + name + "\",\"answers\":{\"serve\":2,\"rally\":1,\"movement\":1,"
                + "\"positioning\":1}}";
    }

    HttpResponse<String> postJson(String path, String json, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        headers.forEach(request::header);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> checkIn(String code, String json) throws Exception {
        return postJson("/api/v1/public/games/" + code + "/guests", json, bootstrap());
    }

    static String guestCookieOf(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("fc_guest="))
                .findFirst()
                .orElseThrow();
    }

    static String tokenOf(String setCookie) {
        return setCookie.substring("fc_guest=".length(), setCookie.indexOf(';'));
    }

    long checkInCalls() {
        return STUB.received().stream()
                .filter(r -> r.path().endsWith("/guest-check-ins"))
                .count();
    }
}
