package com.faircourt.gateway;

import com.faircourt.gateway.routing.GatewayRouteTable;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Real gateway on a random port, with a route table that adds test routes to a {@link StubService}. Each test gets a
 * fresh cookie jar, like a separate browser.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.http.clients.read-timeout=1s", "logging.pattern.level=%5p [%X{requestId:-}]"})
@Import(GatewayTestBase.TestRoutes.class)
abstract class GatewayTestBase {

    static final StubService STUB = StubService.start();

    @TestConfiguration
    static class TestRoutes {

        @Bean
        @Primary
        GatewayRouteTable testRouteTable() {
            return STUB.routeTable();
        }
    }

    @Value("${local.server.port}")
    int port;

    HttpClient browser;

    @BeforeEach
    void newBrowser() {
        STUB.clear();
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    }

    HttpResponse<String> send(String method, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(
                        method,
                        method.equals("GET")
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString("{}"));
        if (!method.equals("GET")) {
            request.header("Content-Type", "application/json");
        }
        headers.forEach(request::header);
        return browser.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        return send("GET", path, Map.of());
    }
}
