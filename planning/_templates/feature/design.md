# FNN design — <Title>

Status: `DRAFT` | `WAITING_APPROVAL` | `APPROVED` (<date>, by owner)

## 1. Summary

<What changes and why, in a few lines. Name the reusable pieces this feature adds or reuses.>

## 2. Blueprint references

- §<n>: "<quote>"

## 3. API contract

For each route:

| Item | Value |
|---|---|
| Route | `<METHOD /api/v1/...>` (§13) |
| Caller | anyone \| signed-in account \| member or scoped guest \| this session's host \| participant |
| Identity source | none \| account principal \| guest cookie (never request IDs) |
| Access check | <AccessPolicy call / ownership rule> |
| Request | <JSON + validation> |
| Response | <status + JSON; list every field> |
| Allowed response keys | <allowlist for non-host audiences> |
| Errors | <status + code per case> |

## 4. Data and transactions

<Tables read/written, migration (new `V<n>` only), invariants (§12), transaction boundary, locks.>

## 5. Domain rules and state transitions

<§11 transitions involved, value objects, edge cases.>

## 6. Backend components

| File | New/changed | Pattern | Notes |
|---|---|---|---|

## 7. Frontend

- Route and data request:
- Components:
- States (loading / success / empty / error; idle / saving / success / conflict):
- Layout at 320–767px and 768px+:

### Copy

| Key | Text | Source (§ quote) or NEW | Approved |
|---|---|---|---|

## 8. Security and privacy checklist

- [ ] Identity derived from principal / guest cookie only
- [ ] Service-level ownership check for host routes
- [ ] Response contains only allowlisted fields for its audience
- [ ] CSRF on for writes; no CORS changes
- [ ] No secrets or personal data in logs or error messages

## 9. Test plan

| TC | Covers | Case (given / when / then) | Layer |
|---|---|---|---|
| TC-01 | AC-01 | | unit / IT / HTTP / visual |

## 10. Open decisions and assumptions

| ID | Question | Options | Recommendation |
|---|---|---|---|

## 11. Out of scope

- <item> → <owning feature or backlog ID>
