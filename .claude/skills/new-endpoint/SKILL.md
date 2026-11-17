---
name: new-endpoint
description: Implement one FairCourt /api/v1 route from the blueprint §13 contract with the correct caller check, a dedicated DTO, transaction boundary, error codes and authorization tests. Refuses routes that are not in §13 unless the owner approved them.
argument-hint: "<METHOD /route from §13>"
---

# /new-endpoint

Route: **$ARGUMENTS**

## 1. Confirm it is in the contract

- Find the route in blueprint §13 (use `/spec-lookup`). Copy its **Caller** and **Contract** columns.
- Not in §13 (besides `GET /api/v1/auth/csrf` from §0) → stop and ask the owner. No generic "assign player by ID" endpoint exists (§7).

## 2. Design checklist (fill in before coding)

```text
Route:            <METHOD /api/v1/...>
Caller (§13):     anyone | signed-in account | member or scoped guest | this session's host | participant
Identity source:  principal | guest cookie | none   (never from request body/path IDs — §13)
Ownership check:  AccessPolicy(principalUserId, sessionId) in the service? (required for every host mutation — §15)
Request DTO:      fields + validation (lengths, UUID format, enum values)
Response DTO:     dedicated class; list fields; confirm no names/bands/other players leak to non-host callers
State changes:    which state transitions (§11) and invariants (§12)
Transaction:      single @Transactional service method? locks needed?
Errors:           400 / 401 / 403 / 404 / 409 cases with error code
Service/package:  owning service (the one that owns the table it writes: conventions.md) + com.faircourt.<service>.<area>
Gateway route:    route-table entry + caller rule in the gateway (AD-14); internal calls to other services listed for approval (AD-15)
```

Show this to the owner if anything in it is not determined by the blueprint.

## 3. Implement

- Controller (thin) → service (rules + ownership) → repository. DTOs only at the controller boundary.
- Gateway route table as first gate (AD-14); in the service, identity only from the gateway's identity token (`CallerIdentity`) and the service-level ownership check as second gate (§15).
- Map DB uniqueness violations to `409 MATCH_CONFLICT`; never report a partial write as success (§15).
- Use the error body `{"code","message","requestId"}` via `GlobalExceptionHandler` (§13).
- Inject `Clock` for anything time-based.
- CSRF stays on for cookie-authenticated writes.

## 4. Tests (required)

- Happy path for the intended caller.
- Each forbidden caller from the §3 permission table: anonymous, other signed-in account, other guest → expected 401/403/404.
- Response body assertion that private fields are absent for non-host callers.
- For mutations: invalid state → 409 and no rows changed.
- Use the PostgreSQL-backed integration test setup, not only an in-memory DB (§17).

## 5. Before finishing

- Run the backend tests; report actual results.
- Consider running the `privacy-reviewer` agent on the diff.
- Do not write into `README.md`: it stays empty by owner request (2026-11-17). Record what the blueprint wants documented there (e.g., the CSRF header) in the feature's `implement.md` and `API Document/`, and tell the owner.
