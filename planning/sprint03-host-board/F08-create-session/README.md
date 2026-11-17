# F08 — Create session

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `BACKLOG` |
| Blueprint | §3 step 1, §11 (creator is sole host), §12 (`play_session`), §13 (`POST /sessions`), §17 checklist ("Host can create a session") |
| Depends on | F03 |
| Waiting for | OD-05 (screen or API only), OD-10 (closing) |
| Suggested branch | `feature/F08-create-session` |
| Last updated | 2026-11-17 |

## Goal

A signed-in account creates a session (title, venue, start/end, 1–4 courts) and becomes its only host; the response gives the shareable public code.

## Scope

- In: `POST /sessions`; validation (title ≤ 120, venue ≤ 160, `endsAt > startsAt`, courts 1–4); public code generation with retry on the unique constraint; `PlaySession` JPA entity; UI per OD-05.
- Out: editing or closing a session (OD-10, L-10), recurring sessions (module 2).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Account creates a session → 201 with `sessionId` and `publicCode`; `host_user_id` = caller | IT |
| AC-02 | The creator can open the board; another account gets 403 | IT |
| AC-03 | Invalid input → 400 `VALIDATION_FAILED` with field info | IT |
| AC-04 | Anonymous → 401; guest → 403 | `AccessMatrixIT` |
| AC-05 | A public-code collision retries and still succeeds | unit (stub generator) |

## Design notes

- `sessions/SessionController`, `sessions/SessionService`, `sessions/PlaySession`, reuse `PublicCodeGenerator`.
- Times accepted as ISO-8601 with offset; stored UTC.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-05 | Form screen or API only | Open |
| OD-10 | Closing sessions | Open |
