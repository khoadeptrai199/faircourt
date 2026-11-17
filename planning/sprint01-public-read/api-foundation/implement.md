# F01 implementation — API foundation (shared library + gateway)

Design: [design.md](design.md) — approved by the owner 2026-11-17 ("start implement first feature"; all four recommendations accepted; Spring Cloud 2025.1.4 approved). Branch `feature/api-foundation`. Depends on Service split (implemented, in review; the owner asked to continue).

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01 | `common-web`: `ErrorCode` (incl. `SERVICE_UNAVAILABLE`), `ApiException`, `ApiError` + unit test that every code has a status and a message | `DONE` |
| T02 | AC-01, AC-06 | `common-web`: `GlobalExceptionHandler`, `RequestIdFilter`, `ServiceSecurityDefaults`; wire into all services | `DONE` |
| T03 | — | Gateway dependency: check compatibility, show the version to the owner, pin it | `DONE` (Spring Cloud 2025.1.4 → Spring Cloud Gateway 5.0.4, `spring-cloud-starter-gateway-server-webmvc`; owner approved 2026-11-17; README version table not written, README is empty by owner request) |
| T04 | AC-04, AC-10 | Gateway: route table, `GatewaySecurityConfig`, JSON handlers, request ID, header sanitizing | `DONE` (see deviations) |
| T05 | AC-09 | Gateway: `DownstreamErrorHandler` + timeouts | `DONE` |
| T06 | AC-02, AC-03 | Gateway: `CsrfController` + `CsrfTokenResponse` (session repository, D-1 = A) | `DONE` |
| T07 | AC-07 | Test support in `common-web`: `TestDatabase`, `@IntegrationTest`, `JsonAllowlist` | `DONE` (published as a test-jar; the three schema ITs now use it) |
| T08 | AC-01…AC-05, AC-09, AC-10 | `ErrorContractIT`, `CsrfBootstrapIT`, `GatewayAccessMatrixIT`, `DownstreamErrorIT`, `RequestIdIT` | `DONE` |
| T09 | AC-02 | Document the CSRF header name as observed, error codes, 401 for unknown routes | `DONE` in `API Document/backend.md` (README stays empty by owner request) |
| T10 | AC-08 | Pre-commit pipeline; record results below | `DONE` (Mac) |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/pom.xml` | changed | Spring Cloud BOM 2025.1.4; `common-web` test-jar managed |
| `backend/libs/common-web/pom.xml` | changed | webmvc, security, `spring-tx`, `jakarta.validation-api` (all versions from the Boot BOM); test-jar; test deps |
| `backend/libs/common-web/src/main/java/com/faircourt/common/web/*` | added | `ErrorCode`, `ApiException`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, `JsonSecurityHandlers`, `ServiceSecurityDefaults`, `CommonWebAutoConfiguration` |
| `backend/libs/common-web/src/main/resources/META-INF/spring/…AutoConfiguration.imports` | added | Auto-configuration registration |
| `backend/libs/common-web/src/test/java/com/faircourt/common/testsupport/*` | added | `IntegrationTest`, `TestDatabase`, `JsonAllowlist`, `TestSupportTest` |
| `backend/libs/common-web/src/test/java/com/faircourt/common/web/**` | added | `ErrorCodeTest`, `RequestIdFilterTest`, `contract/ContractTestApplication`, `contract/ErrorContractIT` |
| `backend/services/gateway/pom.xml` | changed | `common-web`, `spring-cloud-starter-gateway-server-webmvc`, `common-web` test-jar |
| `backend/services/gateway/src/main/java/…/routing/*` | added | `GatewayRoute`, `RouteAccess`, `GatewayRouteTable`, `GatewayRoutes`, `DownstreamErrorHandler` |
| `backend/services/gateway/src/main/java/…/csrf/*` | added | `CsrfController`, `CsrfTokenResponse` |
| `backend/services/gateway/src/main/java/…/security/GatewaySecurityConfig.java` | replaces `SecurityConfig.java` (git: delete + add) | Route-table-driven rules, CSRF, JSON handlers |
| `backend/services/gateway/src/main/resources/application.properties` | changed | Untrusted request ID, timeouts 2 s / 5 s, session cookie flags, log pattern |
| `backend/services/gateway/src/test/java/…/*` | added | `StubService`, `GatewayTestBase`, `GatewayAccessMatrixIT`, `CsrfBootstrapIT`, `DownstreamErrorIT`, `RequestIdIT` |
| `backend/services/{auth,player,playmatch}-service/pom.xml` | changed | `common-web` + test-jar |
| `backend/services/{auth,player,playmatch}-service/…/security/SecurityConfig.java` | changed | Use `ServiceSecurityDefaults.build(http)` |
| `backend/services/{auth,player,playmatch}-service/…/application.properties` | changed | Log pattern with `requestId` |
| `backend/services/{auth,player,playmatch}-service/src/test/…/*SchemaIT.java` | changed | `@IntegrationTest` + `TestDatabase.reset` |
| `API Document/backend.md` | changed | CSRF flow + header name `X-CSRF-TOKEN`, request ID, error code table, 503, status of `GET /auth/csrf` |

Deleted files: `backend/services/gateway/…/security/SecurityConfig.java`. It was moved with `git mv` to `GatewaySecurityConfig.java` and fully rewritten, so git shows it as delete + add (design §6 names the new class).

## Commands executed

All on the Mac (macOS 15.1, `arm64`, Docker 28.0.4, Compose v2.34.0-desktop.1), 2026-11-17.

| Command | Result |
|---|---|
| `curl https://start.spring.io/actuator/info` (bom-ranges) | `spring-cloud 2025.1.4: Spring Boot >=4.0.0 and <4.2.0-M1` |
| `curl` Maven Central: `spring-cloud-dependencies-2025.1.4.pom`, gateway BOM, starter pom, `Last-Modified` | Gateway 5.0.4; `spring-cloud-starter-gateway-server-webmvc` exists (HTTP 200); released 2026-10-22 |
| `jar tf` / `javap` on the downloaded jars | Confirmed `HandlerFunctions.http()`, `BeforeFilterFunctions.uri/removeRequestHeader`, `GatewayRouterFunctions.route(String)`; timeouts are Boot's `spring.http.clients.connect-timeout/read-timeout`; `FilterRegistrationBean`, `OutputCaptureExtension` packages |
| `docker compose run --rm --build backend-format` (1st) | Failed: Maven Central timeout while building the deps image (`repo.maven.apache.org failed to respond`); retried |
| `backend-test` (1st) | `BUILD FAILURE`: `spotless:check` on `ErrorContractIT` (formatter had not run) |
| `backend-format` (2nd) | OK |
| `backend-test` (2nd) | `BUILD FAILURE`: 2 gateway tests; the JDK stub server was single-threaded, so the 3 s "slow" request blocked the next test's request (test bug); fixed with a thread pool |
| `backend-test` (3rd) | `BUILD SUCCESS`, 67 tests |
| `docker compose up --build -d` + `curl` (see evidence) | 6 containers healthy; behaviour as below |
| Changed `RequestIdFilter` to skip `/actuator/health` (health checks every 5 s flooded the logs) | — |
| Pipeline: `backend-format` → frontend `npm run format` → `backend-test` → `format:check` → `build` → staged-files check | all exit 0; `BUILD SUCCESS`, 67 tests, 0 failures; no `.env` / `.idea/` / `target/` staged |
| `docker compose up --build -d`; `docker compose logs --since 15s …` | 0 request-log lines from health checks; `GET /api/v1/auth/csrf` via 9091 → 200 |

Test count by module: `common-web` 17 (`RequestIdFilterTest` 4, `ErrorCodeTest` 3, `TestSupportTest` 4, `ErrorContractIT` 6), `matching` 13, `gateway` 16 (`GatewayHealthIT` 2, `CsrfBootstrapIT` 4, `DownstreamErrorIT` 3, `GatewayAccessMatrixIT` 5, `RequestIdIT` 2), `auth-service` 5, `player-service` 5, `playmatch-service` 11.

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `ErrorContractIT`, gateway ITs | PASS | 400 (3 kinds), 401, 404, 405, 409 (`MATCH_CONFLICT`, `STATE_CONFLICT`), 500, 503 from a service; 401, 403, 503 from the gateway; exact body `{code, message, requestId}` and JSON content type |
| AC-02 | `CsrfBootstrapIT` + live `curl` | PASS | 200 `{"headerName":"X-CSRF-TOKEN","token":…}`; `JSESSIONID` HttpOnly, SameSite=Lax; also through the Vite proxy (9091); documented in `API Document/backend.md` |
| AC-03 | `CsrfBootstrapIT` + live `curl` | PASS | no token / wrong token → 403 `CSRF_TOKEN_INVALID`, stub not reached; bootstrapped token → reaches the stub |
| AC-04 | `GatewayAccessMatrixIT` + live `curl` | PASS | health 200, csrf 200, `/api/v1/anything` 401 JSON, `/` 401, `/internal/v1/anything` 401, path traversal 400/401, stub never reached; routes outside `/api/v1/` rejected at startup |
| AC-05 | `RequestIdIT` (gateway) + `ErrorContractIT` (service) + live logs | PARTIAL | gateway: same ID in response header, error body and log line; service: forwarded ID in header, body and log line. A real gateway → service hop is checked when Public session page adds the first forwarded route |
| AC-06 | `ErrorContractIT` | PASS | integrity / unexpected errors: body has none of `secret`, `Exception`, `constraint`, `at com.` |
| AC-07 | `TestSupportTest` + three `*SchemaIT` | PASS | guard refuses a non-`_test` name and a Flyway config owning other schemas; schema ITs reset through `TestDatabase.reset` |
| AC-08 | Pre-commit pipeline | PASS (Mac) | see commands; WSL not run |
| AC-09 | `DownstreamErrorIT` | PASS | closed port → 503; 3 s response with 1 s read timeout → 503; 409 body passed through byte for byte |
| AC-10 | `RequestIdIT`, `RequestIdFilterTest`, live `curl` | PASS | client `X-Request-Id: evil` → service receives a generated UUID; response header shows the gateway's ID |

## Deviations from the design

- **One request-ID filter for both sides.** `RequestIdFilter` (common-web) has a `trustIncoming` switch: services trust the gateway's ID, the gateway always creates its own and replaces the client header. This replaces the separate `RequestIdGatewayFilter`. It also logs one line per request (method, path, status, ms; not health checks), needed for AC-05.
- **Header sanitizing.** No separate `HeaderSanitizer` class yet: the client's `X-Request-Id` is replaced by the filter above, and every forwarded route removes the browser `Cookie` header (design §6: services receive no browser cookies). The identity header removal comes with Account login.
- **JSON 401/403 handlers in `common-web`**, used by the gateway and every service (design listed them under the gateway).
- **Services: stateless, no CSRF check.** `ServiceSecurityDefaults` disables CSRF and HTTP sessions in services, per design §6 ("services do not handle cookies or CSRF"). Services have no host port, the gateway strips cookies and checks CSRF on every write. Flagged for the review.
- **Other Spring MVC 4xx errors** (e.g. missing parameter, unsupported media type) map to 400 `VALIDATION_FAILED`.
- **No downstream URL properties yet** (`PLAYMATCH_URL` …): no production route forwards to a service until Public session page adds the first one.
- **No per-service `AccessMatrixIT` yet:** services have no route; the first one comes with Public session page.
- **Error messages** in `ErrorCode` are new short texts (only `MATCH_CONFLICT` is from §13). They are API messages; the frontend shows its own approved copy per code. Listed here for the owner's review.
- **README**: CSRF header and error codes documented in `API Document/backend.md`, not README (README is empty by owner request).

## Review fixes (2026-11-17)

After the scope and privacy reviews ([review.md](review.md)), fixed and re-tested on the Mac:

- `HeaderSanitizer` (new, gateway): removes `Cookie`, `Authorization`, `Proxy-Authorization`, `Forwarded`, `X-Forwarded-*` and any `X-Internal-*` header before forwarding. `X-Internal-` is reserved for headers only the gateway sets (the identity token from Account login). This replaces the "Header sanitizing" deviation above.
- `GatewayRoute` now lists its HTTP methods; the security rule and the route predicate both use them.
- Gateway: request cache disabled.
- `GlobalExceptionHandler`: `AccessDeniedException` → 403, `AuthenticationException` → 401; status-carrying errors keep their status (401/403/404/405/409/503), only other 4xx → 400. This replaces the "Other Spring MVC 4xx errors" deviation above.
- `DownstreamErrorHandler`: only `ResourceAccessException` (observed type for refused and timeout).
- `faircourt.request-id.trust-incoming` defaults to `false`; services set `true`.
- New tests: `HeaderSanitizerIT` (2), `CsrfBootstrapIT` +3 (PUT/PATCH/DELETE, token from another session, write on a read-only route), `ErrorContractIT` +2, `AuthServiceAccessIT`, `PlayerServiceAccessIT`, `PlaymatchServiceAccessIT` (2 each).

| Command | Result |
|---|---|
| `backend-format` → frontend `npm run format` → `backend-test` → `format:check` → `build` → staged-files check | all exit 0; `BUILD SUCCESS`, **80 tests**, 0 failures, 0 errors; no `.env` / `.idea/` / `target/` staged |
| `docker compose up --build -d`; `curl` csrf via 9091; `curl` unknown route | 6/6 healthy; 200; 401 |

Test count by module: `common-web` 19, `matching` 13, `gateway` 21, `auth-service` 7, `player-service` 7, `playmatch-service` 13.
