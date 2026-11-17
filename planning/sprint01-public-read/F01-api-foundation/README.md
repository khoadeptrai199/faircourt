# F01 — API foundation: errors, request ID, security route table, CSRF bootstrap, test harness

| Field | Value |
|---|---|
| Sprint | S01 Public read |
| Status | `WAITING_APPROVAL` |
| Blueprint | §0 (API/auth), §13 (errors, status codes), §15 (route rules, auth baseline), §17 (PostgreSQL-backed tests) |
| Depends on | F00 |
| Waiting for | Owner approval of [design.md](design.md) incl. D-1 and AD-01…AD-10 ([decisions](../../architecture/decisions.md)) |
| Suggested branch | `feature/F01-api-foundation` |
| Last updated | 2026-11-17 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

Every later endpoint gets the same error body, request ID, security wiring, CSRF bootstrap and test harness for free, so features F02–F14 only add their own route line, DTO, query/service and test rows. No user-visible change.

## Scope

- In: `ErrorCode` catalog, `ApiException`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`; JSON 401/403 from Spring Security; `SecurityConfig` route table with deny-by-default for `/api/v1/**`; `GET /api/v1/auth/csrf`; integration-test support (`TestDatabase`, `@IntegrationTest`, `JsonAllowlist`, `AccessMatrixIT`, `ErrorContractIT`); README section for the CSRF header and error codes.
- Out: login (F03), guest cookie (F04), any business endpoint (F02+), frontend API client (F02 for reads, F04 for writes).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Every error from `/api/v1/**` (400, 401, 403, 404, 405, 409, 500) has JSON body `{code, message, requestId}` with the matching status | `ErrorContractIT` |
| AC-02 | `GET /api/v1/auth/csrf` as anonymous → 200 with `headerName` and `token`; README states the header name observed in the test | `CsrfBootstrapIT` + README |
| AC-03 | A write without a valid CSRF token → 403 `CSRF_TOKEN_INVALID`; the same write with the bootstrapped token passes the CSRF check | `CsrfBootstrapIT` (test-only endpoint) |
| AC-04 | `/actuator/health` stays public and `UP`; any other `/api/v1/**` path as anonymous → 401 JSON `UNAUTHENTICATED` | `AccessMatrixIT` |
| AC-05 | The `requestId` in an error body appears in the server log line for that request | `ErrorContractIT` (log capture) |
| AC-06 | Error messages never contain exception text, SQL or stack traces | `ErrorContractIT` 500 + 409 cases |
| AC-07 | Integration tests refuse to clean a database whose name does not end in `_test`; `AccessMatrixIT` runs in `mvn verify` | `TestDatabase` + test run |
| AC-08 | Pre-commit pipeline green | pipeline output |

## Open decisions

| ID | Question | Status |
|---|---|---|
| D-1 | CSRF token repository: HTTP session (recommended) vs. JS-readable cookie | Waiting for owner |
| AD-04 | Error code names in the catalog | Waiting for owner |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created; design written, waiting for approval | Planning setup |
