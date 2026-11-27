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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    /** An email starting with this prefix signs in as a second account with its own user id. */
    public static final String OTHER_EMAIL_PREFIX = "other-";

    public static final String OTHER_ACCOUNT_USER_ID = "99999999-9999-9999-9999-999999999999";
    /** An email starting with this prefix makes the stub answer after 3 s (slower than the 1 s test timeout). */
    public static final String SLOW_EMAIL_PREFIX = "slow-";

    /** Stand-ins for auth-service's guest resolve route and playmatch-service's guest check-in route. */
    public static final String RESOLVE_PATH = "/internal/v1/auth/guest-credentials/resolve";

    public static final String GUEST_GAME_ID = "22222222-2222-2222-2222-222222222222";
    /** Game codes that make the check-in stub answer 404, 409 or 500. */
    public static final String UNKNOWN_GAME_CODE = "UNKNOWN1";

    public static final String CLOSED_GAME_CODE = "CLOSED001";
    public static final String FAILING_GAME_CODE = "FAILING1";

    /** Game ids that make the member-join stub answer 404, 409 or 500 (My attendance). */
    public static final String UNKNOWN_GAME_ID = "33333333-3333-3333-3333-333333333333";

    public static final String CLOSED_GAME_ID = "44444444-4444-4444-4444-444444444444";
    public static final String FAILING_GAME_ID = "55555555-5555-5555-5555-555555555555";
    /** An open game for member join and my-attendance. */
    public static final String OPEN_GAME_ID = "66666666-6666-6666-6666-666666666666";

    /** "gameId|userId" to state of the members who joined through the stub. */
    private final Map<String, String> members = new ConcurrentHashMap<>();

    private volatile boolean failingMyAttendance = false;
    /** JSON of the {@code assignment} value the my-attendance stub adds to its answer; null = no assignment key. */
    private volatile String myAssignmentJson = null;

    private final Map<String, String> guestTokens = new ConcurrentHashMap<>();
    private final AtomicInteger guestTokenCounter = new AtomicInteger();
    private volatile boolean resolveFails = false;

    private record VerifyAnswer(int status, String body) {}

    public record Received(String method, String path, String query, Headers headers, String body) {}

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    /** What the stand-in for auth-service answers to the session check (Password reset). */
    private volatile boolean sessionValid = true;

    private volatile boolean sessionCheckFails = false;

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

    public void sessionValid(boolean valid) {
        this.sessionValid = valid;
    }

    /** Makes the session check answer 500, like an auth-service that is up but failing. */
    public void sessionCheckFails(boolean fails) {
        this.sessionCheckFails = fails;
    }

    /** Makes the guest cookie with this token resolve to the attendance (otherwise the resolve route answers 401). */
    public void knownGuestToken(String token, String attendanceId) {
        guestTokens.put(token, attendanceId);
    }

    /** Makes the guest resolve route answer 500, like an auth-service that is up but failing. */
    public void resolveFails(boolean fails) {
        this.resolveFails = fails;
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
        sessionValid = true;
        sessionCheckFails = false;
        guestTokens.clear();
        resolveFails = false;
        members.clear();
        failingMyAttendance = false;
        myAssignmentJson = null;
    }

    /** Makes the my-attendance stub answer 500, like a playmatch-service that is up but failing. */
    public void myAttendanceFails(boolean fails) {
        this.failingMyAttendance = fails;
    }

    /** Makes the my-attendance stub add {@code "assignment": <json>} to its answer (null removes the key). */
    public void myAttendanceAssignment(String json) {
        this.myAssignmentJson = json;
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
                new ArrayList<>(GatewayRouteTable.production(url(), url()).routes());
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
            String userId = email.startsWith(OTHER_EMAIL_PREFIX) ? OTHER_ACCOUNT_USER_ID : ACCOUNT_USER_ID;
            return new VerifyAnswer(200, "{\"userId\":\"" + userId + "\",\"email\":\"" + email + "\"}");
        }
        return new VerifyAnswer(
                401,
                "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in or check in to continue.\",\"requestId\":\"from-service\"}");
    }

    private static String jsonField(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders(),
                requestBody));
        int status = 200;
        String body = "{\"ok\":true}";
        if (path.endsWith("/session-check")) {
            status = sessionCheckFails ? 500 : 200;
            body = "{\"valid\":" + sessionValid + "}";
        } else if (path.equals(VERIFY_PATH)) {
            VerifyAnswer answer = verifyAnswer(requestBody);
            status = answer.status();
            body = answer.body();
        } else if (path.equals(RESOLVE_PATH)) {
            String token = jsonField(requestBody, "token");
            String attendance = guestTokens.get(token);
            if (resolveFails) {
                status = 500;
            } else if (attendance == null) {
                status = 401;
                body = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sign in or check in to continue.\","
                        + "\"requestId\":\"from-service\"}";
            } else {
                body = "{\"gameId\":\"" + GUEST_GAME_ID + "\",\"attendanceId\":\"" + attendance + "\"}";
            }
        } else if (path.startsWith("/internal/v1/games/by-code/") && path.endsWith("/guest-check-ins")) {
            String code =
                    path.substring("/internal/v1/games/by-code/".length(), path.length() - "/guest-check-ins".length());
            if (code.equals(UNKNOWN_GAME_CODE)) {
                status = 404;
                body = "{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"requestId\":\"from-service\"}";
            } else if (code.equals(CLOSED_GAME_CODE)) {
                status = 409;
                body =
                        "{\"code\":\"STATE_CONFLICT\",\"message\":\"This game is closed.\",\"requestId\":\"from-service\"}";
            } else if (code.equals(FAILING_GAME_CODE)) {
                status = 500;
            } else if (jsonField(requestBody, "existingAttendanceId").length() > 0) {
                body = "{\"attendanceState\":\"PENDING\",\"created\":false,\"credential\":null}";
            } else {
                String token = String.format("%043d", guestTokenCounter.incrementAndGet());
                body = "{\"attendanceState\":\"PENDING\",\"created\":true,\"credential\":{\"token\":\"" + token
                        + "\",\"expiresAt\":\"" + Instant.now().plus(Duration.ofHours(14)) + "\"}}";
            }
        } else if (path.startsWith("/internal/v1/games/") && path.endsWith("/member-attendances")) {
            String gameId =
                    path.substring("/internal/v1/games/".length(), path.length() - "/member-attendances".length());
            String userId = jsonField(requestBody, "userId");
            if (gameId.equals(UNKNOWN_GAME_ID)) {
                status = 404;
                body = "{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"requestId\":\"from-service\"}";
            } else if (gameId.equals(CLOSED_GAME_ID)) {
                status = 409;
                body = "{\"code\":\"STATE_CONFLICT\",\"message\":\"This game is closed.\","
                        + "\"requestId\":\"from-service\"}";
            } else if (gameId.equals(FAILING_GAME_ID)) {
                status = 500;
            } else {
                boolean created = members.putIfAbsent(gameId + "|" + userId, "READY") == null;
                body = "{\"attendanceState\":\"READY\",\"created\":" + created + "}";
            }
        } else if (path.startsWith("/internal/v1/games/") && path.endsWith("/my-attendance")) {
            String gameId = path.substring("/internal/v1/games/".length(), path.length() - "/my-attendance".length());
            String query = exchange.getRequestURI().getQuery() == null
                    ? ""
                    : exchange.getRequestURI().getQuery();
            String state = null;
            if (query.startsWith("userId=")) {
                state = members.get(gameId + "|" + query.substring("userId=".length()));
            } else if (query.startsWith("attendanceId=")
                    && gameId.equals(GUEST_GAME_ID)
                    && guestTokens.containsValue(query.substring("attendanceId=".length()))) {
                // The guest's game is GUEST_GAME_ID: playmatch-service applies the game check (design section 3).
                state = "PENDING";
            }
            if (failingMyAttendance) {
                status = 500;
            } else if (state == null) {
                status = 404;
                body = "{\"code\":\"NOT_FOUND\",\"message\":\"Not found.\",\"requestId\":\"from-service\"}";
            } else {
                body = "{\"attendanceState\":\"" + state + "\""
                        + (myAssignmentJson == null ? "" : ",\"assignment\":" + myAssignmentJson) + "}";
            }
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
