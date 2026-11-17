# F01 — API foundation: shared error model, gateway route table, request ID, CSRF bootstrap, test harness

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `WAITING_APPROVAL` |
| Blueprint | §0 (API/auth), §13 (errors, status codes), §15 (route rules, auth baseline), §17 (PostgreSQL-backed tests); OD-17 |
| Depends on | F18 |
| Waiting for | Owner approval of [design.md](design.md) incl. D-1, D-2 and AD-04, AD-06, AD-14, AD-15, AD-18 ([decisions](../../architecture/decisions.md)) |
| Suggested branch | `feature/api-foundation` |
| Last updated | 2026-11-17 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

Every later endpoint, in any service, gets the same error body, request ID, gateway route rule, CSRF bootstrap and test harness for free. No user-visible change.

## Scope

- In: `libs/common-web` (`ErrorCode` incl. `SERVICE_UNAVAILABLE`, `ApiException`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, service security defaults, test support); gateway (dependency per F18 D-3, route table with deny by default, JSON 401/403, request ID creation and forwarding, client header removal, 503 on downstream failure, `GET /api/v1/auth/csrf`); gateway and service ITs; README section for CSRF header and error codes.
- Out: login and identity token (F03), guest cookie (F04), business endpoints (F02+), frontend client (F02, F04).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Every error from `/api/v1/**`, from the gateway or a service (400, 401, 403, 404, 405, 409, 500, 503), has the body `{code, message, requestId}` with the matching status | `ErrorContractIT`, gateway ITs |
| AC-02 | `GET /api/v1/auth/csrf` as anonymous → 200 with `headerName` and `token`; README states the header name observed in the test | `CsrfBootstrapIT` + README |
| AC-03 | A write without a valid CSRF token → 403 `CSRF_TOKEN_INVALID`; with the bootstrapped token it reaches the service | `CsrfBootstrapIT` |
| AC-04 | Health stays public; any other `/api/v1/**` path as anonymous → 401 JSON; `/internal/**` is never forwarded | `GatewayAccessMatrixIT` |
| AC-05 | One `requestId` appears in the gateway log, the service log and the error body of the same request | gateway IT with log capture |
| AC-06 | Error messages never contain exception text, SQL or stack traces | `ErrorContractIT` |
| AC-07 | Integration tests refuse a database whose name does not end in `_test` and reset only their own schema | `TestDatabase` + test run |
| AC-08 | Pre-commit pipeline green | pipeline output |
| AC-09 | Downstream down or slow → 503 `SERVICE_UNAVAILABLE`; downstream error bodies pass through unchanged | `DownstreamErrorIT` |
| AC-10 | A client-sent `X-Request-Id` is replaced by a generated one before forwarding | gateway IT |

## Open decisions

| ID | Question | Status |
|---|---|---|
| D-1 | CSRF token repository at the gateway: HTTP session (recommended) vs. JS-readable cookie | Waiting for owner |
| D-2 | Downstream timeouts (proposal 2 s connect / 5 s read) | Waiting for owner |
| AD-04 | Error code names incl. `SERVICE_UNAVAILABLE` | Waiting for owner |
| F18 D-3 | Gateway technology | Waiting for owner (decided in F18) |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created; design written, waiting for approval | Planning setup |
| 2026-11-17 | Design rewritten for microservices (OD-17): error model moves to `libs/common-web`, route table / CSRF / request ID move to the gateway, new code `SERVICE_UNAVAILABLE`; now depends on F18 | Owner-approved rewrite, conversation 2026-11-17 |
