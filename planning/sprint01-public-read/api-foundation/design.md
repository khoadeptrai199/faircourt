# F01 design — API foundation (shared library + gateway)

Status: `WAITING_APPROVAL` (rewritten 2026-11-17 for microservices, OD-17)

## 1. Summary

Plumbing every endpoint reuses, split between a shared library and the gateway:

| Piece | Where | What |
|---|---|---|
| Error model | `libs/common-web` | `ErrorCode`, `ApiException`, `ApiError`, `GlobalExceptionHandler`; one body `{code, message, requestId}` in every service |
| Request ID | gateway creates, `common-web` reads | Gateway makes a random ID, overwrites any client `X-Request-Id`, forwards it; every service puts it in MDC and error bodies |
| Route table | gateway | One line per §13 route as features land; `/api/v1/**` denied by default; `/internal/**` never routed; JSON 401/403 |
| CSRF bootstrap | gateway | `GET /api/v1/auth/csrf` (§0); CSRF checked on every write at the gateway |
| Downstream failure | gateway | Service down or timeout → `503 SERVICE_UNAVAILABLE` with the standard body |
| Test harness | `common-web` test support + gateway | `TestDatabase` (per schema), `@IntegrationTest`, `JsonAllowlist`, per-service `AccessMatrixIT`, gateway ITs with a JDK `HttpServer` stub |

Depends on F18 (the services exist). Adds the gateway dependency chosen in F18 D-3.

## 2. Blueprint references

- §0: "Add `GET /api/v1/auth/csrf` as a public CSRF bootstrap for browser writes and document the exact request header in README. Do not disable CSRF globally to make forms pass."
- §13: error body `{code, message, requestId}`; 400 / 401 / 403 / 404 / 409 meanings.
- §15: route rules are the first gate, service-level ownership the second; CSRF on cookie-authenticated writes; no open CORS wildcard.
- §17: PostgreSQL-backed integration tests.
- OD-17 (microservices), AD-04, AD-06, AD-14, AD-15, AD-18.

## 3. API contract

### `GET /api/v1/auth/csrf` (served by the gateway itself)

| Item | Value |
|---|---|
| Caller | anyone |
| Identity source | none |
| Response | `200 {"headerName":"<name reported by Spring Security>","token":"<opaque>"}` |
| Side effect | With D-1 option A, the gateway creates its HTTP session (HttpOnly cookie) if none exists |

README documents the header name observed in the test, not a value assumed from documentation.

### Error body (gateway and every service)

`{"code":"<ErrorCode>","message":"<safe text>","requestId":"<uuid>"}`

| Code | HTTP | Raised by |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Services: Bean Validation, unreadable JSON, type mismatch |
| `UNAUTHENTICATED` | 401 | Gateway security entry point |
| `GUEST_ACCESS_EXPIRED` | 401 | Gateway guest handling (F04) |
| `CSRF_TOKEN_INVALID` | 403 | Gateway access-denied handler for CSRF failures |
| `FORBIDDEN` | 403 | Gateway access-denied handler; services' `AccessPolicy` (F06) |
| `NOT_FOUND` | 404 | Unknown resource (services); unknown route under an open prefix |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method |
| `MATCH_CONFLICT` | 409 | playmatch confirm (F10) |
| `STATE_CONFLICT` | 409 | Illegal transitions; fallback for integrity violations |
| `INTERNAL_ERROR` | 500 | Anything unexpected |
| `SERVICE_UNAVAILABLE` | 503 | **New:** gateway or a calling service when a downstream does not answer in time |

When a service returns an error, the gateway passes its status and body through unchanged.

## 4. Data and transactions

None. No migration.

## 5. Domain rules

None.

## 6. Components

### `libs/common-web`

| File | Notes |
|---|---|
| `ErrorCode.java` | enum: HTTP status + default safe message |
| `ApiException.java` | The only exception services throw for expected failures |
| `ApiError.java` | record `code`, `message`, `requestId` |
| `GlobalExceptionHandler.java` | Mapping table below; auto-configured into every servlet service |
| `RequestIdFilter.java` | Services: take `X-Request-Id` (set by the gateway) or create one if missing (direct calls in tests); MDC key `requestId`; cleared in `finally` |
| `ServiceSecurityDefaults.java` | Shared service rule: health public, everything else denied until a feature opens it. Services do not handle cookies or CSRF (they receive no browser cookies) |

Exception mapping (each class name checked against Spring Framework 7 during implementation; tests prove the mapping):

| Exception | Result |
|---|---|
| `ApiException` | its code and message |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `ConstraintViolationException` | 400 `VALIDATION_FAILED` |
| `NoResourceFoundException` | 404 `NOT_FOUND` |
| `HttpRequestMethodNotSupportedException` | 405 `METHOD_NOT_ALLOWED` |
| `DataIntegrityViolationException` (fallback) | 409 `STATE_CONFLICT` |
| any other `Exception` | 500 `INTERNAL_ERROR`, generic message |

### `services/gateway`

| File | Notes |
|---|---|
| `pom.xml` | Adds the gateway dependency per F18 D-3 (exact version shown to the owner before pinning) |
| `GatewayRoutes` (config) | Route table: `/api/v1/auth/csrf` local; feature routes added later (F02 adds `/api/v1/public/sessions/*` → playmatch); nothing under `/internal/**` |
| `GatewaySecurityConfig.java` | Health public; `/api/v1/auth/csrf` public; `/api/v1/**` deny by default; CSRF on; no CORS configuration |
| `JsonSecurityHandlers.java` | Entry point → 401 `UNAUTHENTICATED`; access denied → 403 `CSRF_TOKEN_INVALID` for CSRF failures, else `FORBIDDEN` |
| `RequestIdGatewayFilter` | Creates the ID, overwrites the incoming header, adds it to the response header and MDC |
| `HeaderSanitizer` | Removes client-sent internal headers (`X-Request-Id`, and from F03 the identity header) before forwarding |
| `DownstreamErrorHandler` | Connect/read timeout or refused → 503 `SERVICE_UNAVAILABLE`; timeouts as properties (proposal: connect 2 s, read 5 s) |
| `CsrfController.java` + `CsrfTokenResponse.java` | `GET /api/v1/auth/csrf` |
| `application.properties` | Log pattern with `%X{requestId}`; downstream URLs from env (`PLAYMATCH_URL`, … set in Compose) |

### D-1: CSRF token repository (unchanged question, now at the gateway)

| Option | How | Pros | Cons |
|---|---|---|---|
| **A (recommended)** HTTP-session repository in the gateway | Token stored in the gateway session; SPA reads it from `/auth/csrf`, sends it in the header | All cookies HttpOnly | Anonymous visitors who bootstrap get a gateway session |
| B Cookie repository, JS-readable | Double-submit cookie | Stateless | JavaScript reads a cookie; bootstrap route redundant |

Gateway sessions live in memory: restarting the gateway logs everyone out. Acceptable locally; a shared session store is a later decision.

## 7. Frontend

None in F01. F02 adds the read client; F04 the CSRF write path.

## 8. Security and privacy checklist

- [x] CSRF enabled at the gateway; nothing excluded
- [x] No CORS configuration (same origin through the Vite proxy)
- [x] `/internal/**` never routed; services publish no host ports (F18)
- [x] Client-sent `X-Request-Id` and identity headers are overwritten/removed
- [x] Error messages are our own text; no exception text, SQL, stack traces
- [x] Deny by default at the gateway and in every service

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01, AC-06 | Test-only controller in a test app using `common-web` throws each error type → status, code, generic message, non-empty `requestId` | `ErrorContractIT` (common-web) |
| TC-02 | AC-05 | Same `requestId` in the gateway log, the service log and the error body for one request | gateway IT with stub + log capture |
| TC-03 | AC-02 | Anonymous `GET /api/v1/auth/csrf` → 200, `headerName` and `token` non-empty | `CsrfBootstrapIT` (gateway) |
| TC-04 | AC-03 | Test route `POST` without token → 403 `CSRF_TOKEN_INVALID`; with token + session cookie → reaches the stub | `CsrfBootstrapIT` (gateway) |
| TC-05 | AC-04 | Anonymous: health 200; `/api/v1/auth/csrf` 200; `/api/v1/anything` 401 JSON; `/internal/v1/anything` 401/404 and never reaches the stub | `GatewayAccessMatrixIT` |
| TC-06 | AC-09 | Stub stopped → 503 `SERVICE_UNAVAILABLE`; stub returns 409 body → same 409 body passed through | `DownstreamErrorIT` (gateway) |
| TC-07 | AC-10 | Client sends `X-Request-Id: evil` → the stub receives a different, generated ID | gateway IT |
| TC-08 | AC-07 | `TestDatabase` refuses a database name not ending in `_test`; resets only its own schema | unit + IT |
| TC-09 | AC-08 | Pipeline | pipeline |

Downstream services are replaced in gateway tests by a JDK `com.sun.net.httpserver.HttpServer` started in the test (no new test dependency).

## 10. Open decisions

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | CSRF token repository | A session / B cookie | A |
| AD-04 | Error codes incl. new `SERVICE_UNAVAILABLE` | as listed | Approve |
| D-2 | Downstream timeouts | connect 2 s / read 5 s, or other | 2 s / 5 s |
| — | Log pattern with `requestId` | approve / skip | Approve |

Assumption: anonymous calls to routes that do not exist return 401 (deny by default), not 404, so route existence is not revealed.

## 11. Out of scope

- Login, gateway account session, identity token → F03
- Guest cookie → F04
- `AccessPolicy` → F06
- Frontend client → F02 / F04
