# F08 — Create game

| Field | Value |
|---|---|
| Sprint | S03 Host board |
| Status | `DONE` |
| Blueprint | §3 step 1, §11 (creator is sole host), §12 (`play_session` → `game`), §13 (`POST /sessions` → `POST /games`), §16, §17 checklist ("Host can create a session") |
| Depends on | F03 |
| Waiting for | — (owner commits) |
| Suggested branch | `feature/create-game` |
| Last updated | 2026-11-26 |

Docs: [design.md](design.md) · [implement.md](implement.md) · [review.md](review.md)

## Goal

A signed-in account creates a game (title, venue, day and time, 1–4 courts, singles or doubles, minimum sets per player, optional price per person) and becomes its only host; the response gives the shareable public code and the host lands on the new board.

## Scope

- In: `POST /api/v1/games`; validation (title ≤ 120, venue ≤ 160, `endsAt > startsAt` + time window per OD-26, courts 1–4, format, minimum sets 1–20, price ranges); public code generation with retry; form page `/host/games/new` reached from "Games you host" (per OD-05).
- Out: editing, closing or deleting a game (OD-10, L-10), recurring games (module 2), share button.

## Acceptance criteria (approved with the design, 2026-11-26)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | A signed-in account creates a game → 201 with `gameId`, `publicCode`; `host_user_id` = caller, `state` `OPEN`, fields as sent, times in UTC | IT |
| AC-02 | The creator opens the new board and sees it in "Games you host"; another account gets 403 on its board; anyone reads its public page | IT |
| AC-03 | Each invalid input → 400 `VALIDATION_FAILED`, no row written | IT |
| AC-04 | Anonymous → 401; guest cookie only → 401; no CSRF → 403; token for another route → 401 | `AccessMatrixIT`, `GatewayAccessMatrixIT` |
| AC-05 | A public-code collision retries and still succeeds; 5 collisions → 500, no row | IT (stub generator) |
| AC-06 | Form: defaults, field checks, next-day end, preview line, saving state, error banner, redirect to the board; 320 px and 1280 px, keyboard only | visual (owner) |

Changed from the backlog draft: guest → 401 (not 403), same as every host route (Host board D-3); 400 has no per-field list (shared error model has none; the form checks fields itself).

## Design notes

See [design.md](design.md). No migration: the `game` table already has every column.

## Open decisions

| ID | Question | Status |
|---|---|---|
| OD-05 | Form screen or API only | Decided 2026-11-26: form at `/host/games/new` |
| OD-10 | Closing games | Decided 2026-11-26: out of MVP |
| OD-26 | Allowed start/end times | Decided 2026-11-26: not ended yet + at most 12 hours |
| D-1 | `JdbcClient` vs first JPA entity | Decided 2026-11-26: `JdbcClient` |
| D-2 | Time input: day + start + end (end ≤ start = next day) vs two date-time inputs | Decided 2026-11-26: day + start + end |
| D-3 | Double submit: disabled button only vs idempotency key | Decided 2026-11-26: disabled button only |
| D-4 | Limit on games per account | Decided 2026-11-26: none in pilot |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created (`BACKLOG`) | Claude |
| 2026-11-26 | Design written (`BACKLOG → DESIGN → WAITING_APPROVAL`); OD-26 added | Claude (owner asked: "design Create game đi") |
| 2026-11-26 | Gate 1 (spec) approved by the owner: design.md with all recommendations (OD-05 form, OD-10 out of MVP, OD-26 not ended + ≤ 12 h, D-1 `JdbcClient`, D-2 day + start + end, D-3 disabled button only, D-4 no limit) and the copy table; `WAITING_APPROVAL → APPROVED` | Owner ("duyệt hết như đề xuất") |
| 2026-11-26 | Plan written in `implement.md`: tasks T01–T10, test cases TC-01…TC-18. Waiting for Gate 2 | Claude |
| 2026-11-26 | Gate 2 (plan) approved by the owner: tasks T01–T10 and test cases TC-01…TC-18 in implement.md; `APPROVED → IN_PROGRESS`; branch `feature/create-game` from `main` (`7e76d8a`) | Owner ("duyệt gate 2, làm tiếp đi") |
| 2026-11-26 | Owner approved one extra file outside the plan: the `POST /api/v1/games` `permitAll` line in playmatch `SecurityConfig.java` (identity still checked by `requireAccount`) | Owner (answer to Claude's question) |
| 2026-11-26 | Build loop done: T01–T09 done, T10 live check PASS (visual pending); `scripts/precommit.sh` PIPELINE OK; reviews: privacy-reviewer no high/medium, scope-guardian 2 medium (docs/evidence) → fixed, flow-reviewer 91 → fix round 1 → 93 APPROVE; `IN_PROGRESS → IN_REVIEW` | Claude |
| 2026-11-26 | Gate 3 (result) approved by the owner: UI checked (TC-17, TC-18 PASS); one error text "The game was not created. Try again." kept for every failure, including 503 (review finding 12 accepted, deviation from design §7 "the code's text"); `IN_REVIEW → DONE` | Owner ("giữ 1 câu lỗi, UI ok, chuyển create game DONE") |
