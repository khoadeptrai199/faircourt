# F01 review — API foundation

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `scope-guardian` (fresh agent) | 2026-11-17 | `git diff --cached` on `feature/api-foundation` (56 files) | NEEDS OWNER DECISION |
| `privacy-reviewer` (fresh agent) | 2026-11-17 | same diff | ISSUES FOUND (none exploitable today) |
| Owner | 2026-11-17 | review results + decisions D-R1…D-R6 | Accepted all six recommendations; confirmed `DONE` ("ok do it") |

Neither reviewer ran tests; test results are from the pipeline runs in [implement.md](implement.md).

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| P1 | medium | `gateway/routing/GatewayRoutes.java` | Only `Cookie` was removed before forwarding; a client could later send the identity header that services will trust | `HeaderSanitizer` on every forwarded route removes `Cookie`, `Authorization`, `Proxy-Authorization`, `Forwarded`, `X-Forwarded-*` and the reserved `X-Internal-*` prefix; `HeaderSanitizerIT` | FIXED |
| P2 | medium | `common-web/GlobalExceptionHandler.java` | `AccessDeniedException` / `AuthenticationException` thrown inside a service became 500 | Mapped to 403 `FORBIDDEN` / 401 `UNAUTHENTICATED`; `ErrorContractIT` | FIXED |
| S2 | medium | `common-web/GlobalExceptionHandler.java` | Every status-carrying 4xx error became 400, rewriting 404/409/403 | Map by status (401, 403, 404, 405, 409, 503); only other 4xx → 400; `ErrorContractIT` | FIXED |
| S6 | low | `gateway/routing/DownstreamErrorHandler.java` | Caught every `IOException`, not only downstream failures | Only `ResourceAccessException` (the type observed for refused and timeout) | FIXED |
| P3 | low | `common-web/CommonWebAutoConfiguration.java` | Request-ID trust defaulted to `true` (fail open) | Default `false`; the three services set `true` | FIXED |
| P4 | low | `gateway/security/GatewaySecurityConfig.java` | A PUBLIC route was open for every HTTP method | Routes list their methods; used by the security rule and the route predicate; test: DELETE on a GET-only route → 401, not forwarded | FIXED |
| P5 | low | `GatewaySecurityConfig.java` | Denied requests could be saved into a new session (request cache) | `requestCache(disable)` | FIXED |
| S5 | low | `implement.md` | AC-05 marked PASS although the real gateway → service hop is not tested yet | AC-05 → PARTIAL | FIXED |
| S8 | low | `decisions.md` | AD-06 still PROPOSED after the owner chose the session repository | AD-06 → ACCEPTED 2026-11-17 | FIXED |
| S-del | info | `implement.md` | "Deleted files: none" was wrong: gateway `SecurityConfig.java` shows as delete + add | Corrected | FIXED |
| S3-claude | info | `CLAUDE.md` | "error codes as in `ErrorCode`" could read as approving the messages | Now "error code names"; messages marked not approved | FIXED |
| — | missing tests | gateway, services | Cross-session CSRF token, PUT/PATCH/DELETE without token, GET with `Cookie`, real service chains deny `/api/v1/**` and `/internal/**` | `CsrfBootstrapIT` (+3), `HeaderSanitizerIT`, `Auth/Player/PlaymatchServiceAccessIT` | FIXED |
| S1 | needs owner | `common-web/ServiceSecurityDefaults.java` | CSRF and sessions are off in the three services | Owner decision D-R1 | ACCEPTED (owner 2026-11-17) |
| S3 | needs owner | `common-web/ErrorCode.java` | 10 of 11 error messages are new text (only `MATCH_CONFLICT` is from §13) | Owner decision D-R2 | ACCEPTED (owner 2026-11-17) |
| S4 | needs owner | `common-web/RequestIdFilter.java` | New log line per request (method, path, status, ms; not health checks) | Owner decision D-R3 | ACCEPTED (owner 2026-11-17) |
| S5b | needs owner | design §1/§6 | Per-service `AccessMatrixIT` rows and the real gateway → service request-ID check move to Public session page (first forwarded route) | Owner decision D-R4 | ACCEPTED (owner 2026-11-17) |
| S7 | needs owner | `common-web/pom.xml` | New library dependencies (all versions from the Boot BOM): `spring-boot-starter-webmvc`, `-security`, `spring-tx`, `jakarta.validation-api`; test: `-webmvc-test`, `-security-test`, `-validation`, `spring-jdbc`, `flyway-core`; test-jar build | Owner decision D-R5 | ACCEPTED (owner 2026-11-17) |
| P7 | needs owner | design §10 assumption | Unknown routes answer 401 (anonymous), not 404, so route existence is not revealed | Owner decision D-R6 | ACCEPTED (owner 2026-11-17) |
| P6 | low, note | gateway `application.properties` | `COOKIE_SECURE` defaults to false when missing; fine locally, risky for a future HTTPS deployment | Keep for local; revisit at deployment (later backlog, pilot) | OPEN (note) |

## Owner decisions needed

| ID | Question | Recommendation |
|---|---|---|
| D-R1 | Services skip the CSRF check and never create sessions; the gateway checks CSRF and strips cookies; services have no host port | Accept (privacy review: safe as built while services stay unpublished and trust no browser-set header) |
| D-R2 | Error message texts in `ErrorCode` | Accept as API texts; the frontend shows its own approved copy per code |
| D-R3 | One log line per request | Accept (needed to trace a request across services; no query, body, headers or cookies) |
| D-R4 | Move per-service access-matrix rows and the real hop check to Public session page | Accept |
| D-R5 | `common-web` dependencies listed above | Accept |
| D-R6 | Unknown route → 401, not 404 | Accept |

## Acceptance trace

| AC | Implementation | Test / check | Verdict |
|---|---|---|---|
| AC-01 | `ErrorCode`, `ApiError`, `GlobalExceptionHandler`, `JsonSecurityHandlers` | `ErrorContractIT` (8), gateway ITs | PASS |
| AC-02 | `CsrfController` | `CsrfBootstrapIT`, live `curl` via 9091 | PASS |
| AC-03 | `GatewaySecurityConfig` (CSRF on, session repo) | `CsrfBootstrapIT` (7) | PASS |
| AC-04 | `GatewayRouteTable`, `GatewayRoute`, `GatewaySecurityConfig` | `GatewayAccessMatrixIT`, service `*AccessIT` | PASS |
| AC-05 | `RequestIdFilter` | `RequestIdIT`, `ErrorContractIT` | PARTIAL, accepted by the owner (D-R4): the real gateway → service hop is checked in Public session page |
| AC-06 | `GlobalExceptionHandler` | `ErrorContractIT` | PASS |
| AC-07 | `TestDatabase`, `@IntegrationTest` | `TestSupportTest`, three `*SchemaIT` | PASS |
| AC-08 | — | pipeline 2026-11-17, Mac | PASS (Mac) |
| AC-09 | `DownstreamErrorHandler`, timeouts | `DownstreamErrorIT` | PASS |
| AC-10 | `RequestIdFilter` (gateway mode), `HeaderSanitizer` | `RequestIdIT`, `HeaderSanitizerIT` | PASS |

## Gate

- Pre-commit pipeline: `PASS` (2026-11-17, Mac): 80 tests, 0 failures; frontend `format:check` + `build`; stack 6/6 healthy.
- README / `API Document` updated: `API Document/backend.md` yes; README stays empty by owner request.
- Result: `PASSED`. No open high or medium finding; owner accepted D-R1…D-R6 and confirmed `DONE` on 2026-11-17.
- Not verified: Windows WSL; the gateway → real service hop (no production route forwards yet).
