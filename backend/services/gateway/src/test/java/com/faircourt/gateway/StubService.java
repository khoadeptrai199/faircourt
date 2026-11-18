package com.faircourt.gateway;

import com.faircourt.gateway.routing.GatewayRoute;
import com.faircourt.gateway.routing.GatewayRouteTable;
import com.faircourt.gateway.routing.RouteAccess;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.springframework.http.HttpMethod;

/**
 * Stand-in for a downstream service in gateway tests: a JDK {@link HttpServer} (no extra test dependency). It records
 * every request it receives.
 *
 * <ul>
 *   <li>{@code /api/v1/test/echo} → 200 {@code {"ok":true}}
 *   <li>{@code /api/v1/test/conflict} → 409 with a service error body
 *   <li>{@code /api/v1/test/slow} → answers after 3 s
 * </ul>
 */
public final class StubService {

    public static final String CONFLICT_BODY =
            "{\"code\":\"MATCH_CONFLICT\",\"message\":\"A player or court is no longer available.\","
                    + "\"requestId\":\"from-service\"}";

    /** Stand-in for auth-service's internal verify route (Account login). */
    public static final String VERIFY_PATH = "/internal/v1/auth/verify";

    public static final String ACCOUNT_PASSWORD = "right-password";
    public static final String ACCOUNT_USER_ID = "11111111-1111-1111-1111-111111111111";
    /** An email starting with this prefix makes the stub answer after 3 s (slower than the 1 s test timeout). */
    public static final String SLOW_EMAIL_PREFIX = "slow-";

    private record VerifyAnswer(int status, String body) {}

    public record Received(String method, String path, Headers headers, String body) {}

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    private StubService() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        // One thread per request: the slow case must not block the next test's request.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public static StubService start() {
        return new StubService();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Received> received() {
        return new ArrayList<>(received);
    }

    public void clear() {
        received.clear();
    }

    /** A port on which nothing listens, for the "service is down" case. */
    public static String closedPortUrl() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Production route table (its service routes pointed at this stub) plus test routes: {@code /api/v1/test/**} (all methods) and {@code /api/v1/readonly/**}
     * (GET only) → this stub; {@code /api/v1/down/**} → a closed port.
     */
    public GatewayRouteTable routeTable() {
        List<GatewayRoute> routes =
                new ArrayList<>(GatewayRouteTable.production(url()).routes());
        Set<HttpMethod> all =
                Set.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
        routes.add(new GatewayRoute("test-stub", all, "/api/v1/test/**", RouteAccess.PUBLIC, url()));
        routes.add(new GatewayRoute(
                "test-readonly", Set.of(HttpMethod.GET), "/api/v1/readonly/**", RouteAccess.PUBLIC, url()));
        routes.add(new GatewayRoute(
                "test-down", Set.of(HttpMethod.GET), "/api/v1/down/**", RouteAccess.PUBLIC, closedPortUrl()));
        return new GatewayRouteTable(routes);
    }

    /** Right password -> 200 with the account; anything else -> 401 like auth-service; "slow-" emails answer late. */
    private static VerifyAnswer verifyAnswer(String requestBody) {
        String email = jsonField(requestBody, "email");
        String password = jsonField(requestBody, "password");
        if (email.startsWith(SLOW_EMAIL_PREFIX)) {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ACCOUNT_PASSWORD.equals(password)) {
            return new VerifyAnswer(200, "{\"userId\":\"" + ACCOUNT_USER_ID + "\",\"email\":\"" + email + "\"}");
        }
        return new VerifyAnswer(
                401,
                "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in or check in to continue.\",\"requestId\":\"from-service\"}");
    }

    private static String jsonField(String json, String name) {
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(exchange.getRequestMethod(), path, exchange.getRequestHeaders(), requestBody));
        int status = 200;
        String body = "{\"ok\":true}";
        if (path.equals(VERIFY_PATH)) {
            VerifyAnswer answer = verifyAnswer(requestBody);
            status = answer.status();
            body = answer.body();
        } else if (path.endsWith("/conflict")) {
            status = 409;
            body = CONFLICT_BODY;
        } else if (path.endsWith("/slow")) {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
