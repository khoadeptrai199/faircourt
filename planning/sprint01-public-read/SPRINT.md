# S01 — Services + public read

| Field | Value |
|---|---|
| Status | `ACTIVE` |
| Goal | The backend runs as a gateway + `auth-service` + `player-service` + `playmatch-service` with one schema each, started by one command; anyone with the link sees the game (time, venue, courts, singles/doubles, minimum sets, price, status) through the gateway and nothing private; every service shares one error shape, request ID and test harness |
| Gate | All services healthy with one command on WSL (and Mac); §17 step 1 tests still green after the split; "anonymous sees only public data" through the gateway (first half of §17 step 2); §17 checklist line 1: "Anonymous public response contains no attendance names, bands, match IDs or guest credentials" |
| Blueprint | §0, §3, §12, §13, §15, §16; OD-17…OD-20 |
| Started / finished | 2026-11-17 / — |

## Features

| ID | Feature | Status |
|---|---|---|
| F18 | [Service split skeleton](service-split/README.md) | `DONE` |
| F01 | [API foundation: shared lib + gateway](api-foundation/README.md) | `DONE` |
| F19 | [Game rename ("session" → "game")](game-rename/README.md) | `DONE` |
| F02 | [Public game page + frontend foundation](public-game-page/README.md) | `DONE` |
| F20 | [Look and feel ("Court lines")](look-and-feel/README.md) | `IN_REVIEW` |

Order: F18 → F01 → F19 → F02 (backend first, then F02's frontend) → F20.

## Why this sprint first

Read-only and anonymous: no login, no transactions, no state machine. It still exercises the whole path (browser → Vite proxy → gateway → playmatch-service → query → PostgreSQL) and builds the plumbing (services, schemas, errors, gateway route table, API client, async-state hook, test harness) so later sprints only add features.

## Demo at sprint end

1. `docker compose up --build -d`; `docker compose ps` shows postgres, gateway, auth-service, player-service, playmatch-service, frontend healthy.
2. Read the public code: `docker compose logs playmatch-service | grep "public code"`.
3. Open `http://localhost:9091/g/<code>` at phone width: title, venue, Vietnam time, 2 courts, Doubles, at least 6 sets per player, price, status; no names.
4. Open `http://localhost:9091/g/WRONGCODE`: friendly not-found.
5. `curl -s http://localhost:9090/api/v1/public/games/<code>`: only allowlisted fields.
6. `curl -s http://localhost:9090/api/v1/auth/csrf`: header name + token.
7. `docker compose stop playmatch-service`, reload the page: error with Retry (503); start it again.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro

- 2026-11-17: sprint re-scoped for microservices (OD-17). F18 added to this sprint as part of the owner-approved sprint plan rewrite ("oke" to rewriting the planning documents, conversation 2026-11-17). Folder name `sprint01-public-read` kept to avoid moving files.
- 2026-11-18: F19 Game rename added to this sprint before F02 (owner approved the rename plan as a separate feature, "duyệt hết"). F19 and F02 built on one branch `feature/public-game-page`.
- 2026-11-18: F20 Look and feel added at the owner's request ("UI bạn tự vẽ … chất riêng").
