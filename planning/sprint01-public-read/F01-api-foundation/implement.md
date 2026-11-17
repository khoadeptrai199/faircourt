# F01 implementation — API foundation

Design: [design.md](design.md) — status `WAITING_APPROVAL`. No task starts before approval.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-01 | `ErrorCode`, `ApiException`, `ApiError` + unit test that every code has a 4xx/5xx status and a message | `TODO` |
| T02 | AC-05 | `RequestIdFilter` (first in chain) + log pattern | `TODO` |
| T03 | AC-01, AC-06 | `GlobalExceptionHandler` with the mapping table | `TODO` |
| T04 | AC-03, AC-04 | `JsonSecurityHandlers`; restructure `SecurityConfig` into the route table (health, `/auth/csrf`, `/api/v1/**` deny) | `TODO` |
| T05 | AC-02 | `CsrfController` + `CsrfTokenResponse` (per D-1) | `TODO` |
| T06 | AC-07 | Test support: `TestDatabase`, `@IntegrationTest`, `JsonAllowlist`; move `DatabaseSchemaIT` onto `TestDatabase` | `TODO` |
| T07 | AC-01…AC-05 | `ErrorContractIT`, `CsrfBootstrapIT` (test-only endpoints + test security chain), `AccessMatrixIT` | `TODO` |
| T08 | AC-02 | README: CSRF header name as observed, error code table, 401-vs-403 change for unknown routes; `API Document/backend.md` status for `GET /auth/csrf` | `TODO` |
| T09 | AC-08 | Run the pre-commit pipeline; record results below | `TODO` |

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
| AC-04 | `AccessMatrixIT` | | |
| AC-05 | `ErrorContractIT` log capture | | |
| AC-06 | `ErrorContractIT` | | |
| AC-07 | `TestDatabase` guard + test run | | |
| AC-08 | Pre-commit pipeline | | |

## Deviations from the design

- none yet
