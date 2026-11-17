# F01 design — API foundation

Status: `WAITING_APPROVAL` (2026-11-17)

## 1. Summary

Backend plumbing that every endpoint reuses:

1. one error body `{code, message, requestId}` for every failure, including security failures;
2. a random request ID per request, in logs (MDC) and in error bodies;
3. a Spring Security route table for `/api/v1` that mirrors §13, deny by default, with JSON 401/403;
4. the CSRF bootstrap `GET /api/v1/auth/csrf` required by §0;
5. an integration-test harness: safe DB reset, an access matrix, and a JSON allowlist helper.

No new dependency, no migration, no frontend change.

## 2. Blueprint references

- §0: "Add `GET /api/v1/auth/csrf` as a public CSRF bootstrap for browser writes and document the exact request header in README. Do not disable CSRF globally to make forms pass."
- §13: "Error body: `{"code":"MATCH_CONFLICT","message":"A player or court is no longer available.","requestId":"..."}`. Use `400` invalid payload, `401` missing/expired identity, `403` authenticated but no permission, `404` unknown or private resource not visible, `409` state conflict. Validate field lengths and IDs at the boundary, and show friendly UI messages rather than raw exceptions."
- §15: "Spring Security route rules provide an initial gate, and service-level ownership does the resource-specific check." "CSRF token on cookie-authenticated writes; no open CORS wildcard."
- §17: "use a PostgreSQL-backed integration test rather than relying only on an in-memory database."

## 3. API contract

### `GET /api/v1/auth/csrf`

| Item | Value |
|---|---|
| Caller | anyone |
| Identity source | none |
| Response | `200 {"headerName":"<name reported by Spring Security>","token":"<opaque>"}` |
| Errors | none expected |
| Side effect | With D-1 option A, creates an HTTP session (HttpOnly `JSESSIONID`) for the browser if none exists |

The README documents the header name observed in `CsrfBootstrapIT`, not a value assumed from documentation.

### Error body (all `/api/v1/**`)

`{"code":"<ErrorCode>","message":"<safe text>","requestId":"<uuid>"}`

Catalog (AD-04; only `MATCH_CONFLICT` comes from the blueprint, the rest need approval):

| Code | HTTP | Raised by |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation failures, unreadable JSON, type mismatches |
| `UNAUTHENTICATED` | 401 | Security entry point (no identity) |
| `GUEST_ACCESS_EXPIRED` | 401 | Guest filter (F04) |
| `CSRF_TOKEN_INVALID` | 403 | Access-denied handler for missing/invalid CSRF token |
| `FORBIDDEN` | 403 | Access-denied handler; `AccessPolicy` (F06) |
| `NOT_FOUND` | 404 | Unknown route or resource; services |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method |
| `MATCH_CONFLICT` | 409 | `MatchService.confirm` (F10) |
| `STATE_CONFLICT` | 409 | Illegal transitions (F07, F11); fallback for `DataIntegrityViolationException` |
| `INTERNAL_ERROR` | 500 | Anything unexpected |

## 4. Data and transactions

None. No migration.

## 5. Domain rules

None. This feature adds no business behavior.

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|
| `common/ErrorCode.java` | new | Error catalog | enum: HTTP status + default safe message |
| `common/ApiException.java` | new | — | `RuntimeException` carrying `ErrorCode` + safe message; the only exception services throw for expected failures |
| `common/ApiError.java` | new | DTO record | `code`, `message`, `requestId` |
| `common/GlobalExceptionHandler.java` | new | Central handler | Mapping table below; logs 5xx at ERROR and 409 fallbacks at WARN with `requestId` |
| `common/RequestIdFilter.java` | new | Servlet filter | Runs first; random UUID → MDC key `requestId` + request attribute; cleared in `finally` |
| `identity/SecurityConfig.java` | changed | Route table | One `requestMatchers` line per §13 route as features land, each with a comment naming its §13 caller; `/api/v1/**` `denyAll` last; JSON handlers; CSRF default on; no CORS configuration |
| `identity/JsonSecurityHandlers.java` | new | Adapter | `AuthenticationEntryPoint` → 401 `UNAUTHENTICATED`; `AccessDeniedHandler` → 403 `CSRF_TOKEN_INVALID` for CSRF exceptions, else `FORBIDDEN` |
| `identity/CsrfController.java` | new | Thin controller | `GET /api/v1/auth/csrf` returns `CsrfTokenResponse(headerName, token)` |
| `identity/CsrfTokenResponse.java` | new | DTO record | — |
| `application.properties` | changed | — | Log pattern includes `%X{requestId}` (exact property checked against the Spring Boot 4 reference at implementation) |

Exception mapping in `GlobalExceptionHandler`:

| Exception | Result |
|---|---|
| `ApiException` | its `ErrorCode` and message |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `ConstraintViolationException` | 400 `VALIDATION_FAILED` |
| `NoResourceFoundException` | 404 `NOT_FOUND` |
| `HttpRequestMethodNotSupportedException` | 405 `METHOD_NOT_ALLOWED` |
| `DataIntegrityViolationException` (fallback; services translate first where they know better) | 409 `STATE_CONFLICT`, transaction already rolled back |
| any other `Exception` | 500 `INTERNAL_ERROR`, generic message |

Exception class names are Spring Framework 7 / Jakarta names as I know them; each is checked against the reference during implementation and the test suite proves the mapping.

### D-1: CSRF token repository (needs owner decision)

| Option | How it works | Pros | Cons |
|---|---|---|---|
| **A (recommended)** Spring's default HTTP-session repository | Token stored server-side; SPA reads it from `/auth/csrf` and sends it in the header | All cookies stay HttpOnly; nothing for JavaScript to read | Anonymous visitors who bootstrap get an HTTP session |
| B Cookie repository with a JS-readable token cookie | Double-submit cookie | Stateless | Adds a cookie JavaScript must read; the bootstrap route becomes redundant |

With A, Spring changes the token when a user logs in or out, so the frontend client fetches a fresh token after login/logout (F03) and retries once on `CSRF_TOKEN_INVALID`.

## 7. Frontend

None in F01. `api/client.ts` arrives in F02 (reads) and gains the CSRF write path in F04, using this contract.

## 8. Security and privacy checklist

- [x] CSRF stays enabled; nothing is excluded from it
- [x] No CORS configuration (same origin through the Vite proxy)
- [x] Error messages are our own text; no exception messages, SQL, stack traces
- [x] `requestId` is random and not linked to a user
- [x] Deny by default: a route is reachable only after its feature adds its line

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01, AC-06 | A test-only controller throws each error type → expected status, code, generic message, non-empty `requestId` | `ErrorContractIT` |
| TC-02 | AC-05 | Error response `requestId` equals the MDC value captured in the log output | `ErrorContractIT` |
| TC-03 | AC-02 | Anonymous `GET /api/v1/auth/csrf` → 200, `headerName` and `token` non-empty | `CsrfBootstrapIT` |
| TC-04 | AC-03 | Test-only `POST` without token → 403 `CSRF_TOKEN_INVALID`; with bootstrapped token + session cookie → reaches handler | `CsrfBootstrapIT` |
| TC-05 | AC-04 | Rows: anonymous `GET /actuator/health` → 200; anonymous `GET /api/v1/auth/csrf` → 200; anonymous `GET /api/v1/anything` → 401 JSON | `AccessMatrixIT` |
| TC-06 | AC-07 | `TestDatabase.reset()` refuses a non-`_test` database name | unit test with a stub name, plus existing guard |
| TC-07 | AC-08 | Full pre-commit pipeline | pipeline |

Test-only endpoints live in `src/test` under `/test-only/**`, served by a test-only `SecurityFilterChain` with higher priority and CSRF enabled. They never ship in the jar.

Test support (in `src/test/java/com/faircourt/support/`):

| File | Purpose |
|---|---|
| `TestDatabase.java` | Checks the DB name ends with `_test`, then `flyway.clean()` + `flyway.migrate()` (logic moved from `DatabaseSchemaIT`) |
| `IntegrationTest.java` | Meta-annotation: `@SpringBootTest` with `spring.flyway.clean-disabled=false` + MockMvc + `TestDatabase` |
| `JsonAllowlist.java` | Asserts that a JSON object's keys are a subset of an allowed set (used from F02 on) |
| `api/AccessMatrixIT.java` | Parameterized rows `(method, path, caller, expected status)`; callers grow with features (anonymous now; host, other account, guest, other guest later) |

`DatabaseSchemaIT` switches to `TestDatabase` (same behavior, no test removed).

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | CSRF token repository | A session / B cookie | A |
| AD-04 | Error code names | as listed | Approve as listed |
| — | Log pattern change to show `requestId` | approve / skip | Approve; without it AC-05 cannot be checked from logs |

Assumption: anonymous calls to routes that do not exist yet return 401 (deny by default) instead of 404. This does not reveal which routes exist. Today the same calls return 403 (README verification log); the README will be updated.

## 11. Out of scope

- Login and account session → F03
- Guest cookie and `GUEST_ACCESS_EXPIRED` raising → F04
- `AccessPolicy` → F06
- Frontend client → F02/F04
