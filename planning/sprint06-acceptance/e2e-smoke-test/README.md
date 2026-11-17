# F15 — End-to-end smoke test + acceptance check

| Field | Value |
|---|---|
| Sprint | S06 Acceptance |
| Status | `BACKLOG` |
| Blueprint | §0 "Required end-to-end demonstration" steps 1–6, automated checks paragraph; §17 scope checklist |
| Depends on | F13 (all functional features) |
| Waiting for | OD-08 (browser test tool, optional) |
| Suggested branch | `feature/e2e-smoke-test` |
| Last updated | 2026-11-17 |

## Goal

One automated HTTP-level test walks §0 steps 1–5 with separate cookie jars (anonymous, host, guest, another signed-in player), and a recorded `/acceptance-check` run covers all six steps including the restart.

## Scope

- In: `HostGuestFlowIT` over real HTTP (random port) with CSRF bootstrap per jar; acceptance-check run with evidence in `implement.md`; README verification log update.
- Out: browser automation unless OD-08 c is approved.

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | §0 steps 1–5 pass in `HostGuestFlowIT`, including stale confirm → 409 with no partial rows | IT |
| AC-02 | §0 step 6: `docker compose down` + `up -d` (no `-v`) keeps session and finished match; host and guest pages load; guest cookie still valid | `/acceptance-check` evidence |
| AC-03 | Every §17 checklist line `PASS` with evidence | `/acceptance-check checklist` |
