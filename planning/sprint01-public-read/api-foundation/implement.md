# F01 implementation — API foundation (shared library + gateway)

Design: [design.md](design.md) — status `WAITING_APPROVAL`. No task starts before approval. Requires F18 `DONE` or the owner's OK to overlap.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01 | `common-web`: `ErrorCode` (incl. `SERVICE_UNAVAILABLE`), `ApiException`, `ApiError` + unit test that every code has a status and a message | `TODO` |
| T02 | AC-01, AC-06 | `common-web`: `GlobalExceptionHandler`, `RequestIdFilter`, `ServiceSecurityDefaults`; wire into all services | `TODO` |
| T03 | — | Gateway dependency per F18 D-3: check the compatibility page, show the version to the owner, pin it, README version table | `TODO` |
| T04 | AC-04, AC-10 | Gateway: route table, `GatewaySecurityConfig`, `JsonSecurityHandlers`, `RequestIdGatewayFilter`, `HeaderSanitizer` | `TODO` |
| T05 | AC-09 | Gateway: `DownstreamErrorHandler` + timeouts | `TODO` |
| T06 | AC-02, AC-03 | Gateway: `CsrfController` + `CsrfTokenResponse` (per D-1) | `TODO` |
| T07 | AC-07 | Test support in `common-web`: `TestDatabase` (per schema), `@IntegrationTest`, `JsonAllowlist` | `TODO` |
| T08 | AC-01…AC-05, AC-09, AC-10 | `ErrorContractIT`, `CsrfBootstrapIT`, `GatewayAccessMatrixIT`, `DownstreamErrorIT` (JDK `HttpServer` stub) | `TODO` |
| T09 | AC-02 | README: CSRF header name as observed, error code table, 401 for unknown routes; `API Document/backend.md` status for `GET /auth/csrf` | `TODO` |
| T10 | AC-08 | Pre-commit pipeline; record results below | `TODO` |

## Files

| File | Added / changed | Why |
|---|---|---|

Deleted files: none.

## Commands executed

| Date | Machine | Command | Result |
|---|---|---|---|

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `ErrorContractIT` | | |
| AC-02 | `CsrfBootstrapIT` + README | | |
| AC-03 | `CsrfBootstrapIT` | | |
| AC-04 | `GatewayAccessMatrixIT` | | |
| AC-05 | requestId across gateway + service logs | | |
| AC-06 | `ErrorContractIT` | | |
| AC-07 | `TestDatabase` guard | | |
| AC-08 | Pre-commit pipeline | | |
| AC-09 | `DownstreamErrorIT` | | |
| AC-10 | Header overwrite IT | | |

## Deviations from the design

- none yet
