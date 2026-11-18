# F19 — Game rename ("session" → "game")

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `DONE` |
| Blueprint | §12 (tables), §13 (routes), §16 (routes); OD-22 |
| Depends on | F18, F01 |
| Waiting for | — |
| Branch | `feature/public-game-page` (shared with F02, owner 2026-11-18: one new branch, not `main`) |
| Last updated | 2026-11-18 |

Docs: [design.md](design.md) · [implement.md](implement.md) · [review.md](review.md)

## Goal

The play event a host posts is called a **game** everywhere: database, Java names, API URLs, page URLs and copy. A single 21-point contest is a **match** in code and a **set** in the UI. "Session" stays only for the login/HTTP session.

## Scope

- In: Flyway `V2` in playmatch, auth and player services (rename table, columns, constraints, index; data kept); Java package and seed; matcher field `gamesPlayed` → `matchesPlayed`; routes for the public page (`/public/games/...`, `/g/:publicCode`); home page copy; rules for agents, API Document, feature folder names (`public-game-page`, `create-game`, `my-match-assignment`).
- Out: rewriting every older planning document (they keep "session"; `conventions.md` "Domain words" says how to read it); seeded demo title in an existing local database (data, not code; see implement.md).

## Acceptance criteria

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | On a database where V1 is applied with data, V2 renames `play_session` → `game`, every `session_id` → `game_id`, `games_played` → `matches_played`, and keeps all rows | live stack: Flyway log + row counts |
| AC-02 | No table, column, constraint or index name in the playmatch schema contains `session` or `games_played`; auth and player use `game_id` | `PlaymatchSchemaIT`, `AuthSchemaIT`, `PlayerSchemaIT` |
| AC-03 | Seed stays idempotent across the rename (existing demo game recognised, not seeded twice) | `PlaymatchSchemaIT.seedIsIdempotent` + live log "already present" |
| AC-04 | Matcher behaviour unchanged (only the field name changed) | `MatchSuggesterTest` 13/13 |
| AC-05 | Pipeline green | pre-commit pipeline |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-18 | Owner: replace "session" with "game" in API URLs, class names and copy (OD-22). A 21-point contest stays match (code) / set (UI) | Owner, conversation 2026-11-18 |
| 2026-11-18 | Owner approved the rename plan as proposed (separate feature before the public page; table `game`, column `game_id`, API `/public/games/{code}`, page `/g/:code`) and asked to implement on a new branch: "duyệt hết, triển khai implement đi. nhớ checkout branch mới đừng làm trên main" | Owner, conversation 2026-11-18 |
| 2026-11-18 | Review: no open high/medium finding (low findings fixed; "Page not found" and one-bound price approved by the owner). Pipeline green. Owner: "mọi thứ ok thì build + test + commit + push" → `DONE`, committed and pushed on `feature/public-game-page` | review.md, owner |
