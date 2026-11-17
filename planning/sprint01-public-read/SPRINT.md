# S01 — Public read

| Field | Value |
|---|---|
| Status | `PLANNED` (next) |
| Goal | Anyone with the link sees tonight's session (time, venue, courts, status) and nothing private; the API has one error shape, CSRF bootstrap and a test harness every later feature reuses |
| Gate | "anonymous sees only public data" (first half of the §17 step 2 gate); §17 checklist line 1: "Anonymous public response contains no attendance names, bands, match IDs or guest credentials" |
| Blueprint | §0, §3, §13, §15, §16 |
| Started / finished | — |

## Features

| ID | Feature | Status |
|---|---|---|
| F01 | [API foundation](F01-api-foundation/README.md) | `WAITING_APPROVAL` |
| F02 | [Public session page + frontend foundation](F02-public-session-page/README.md) | `WAITING_APPROVAL` |

## Why this sprint first

Read-only and anonymous: no login, no transactions, no state machine. It still exercises the whole path (browser → Vite proxy → security → controller → query → PostgreSQL) and builds the plumbing (errors, security route table, API client, async-state hook, test harness) so later sprints only add features.

## Demo at sprint end

1. `docker compose up --build -d`; read the public code with `docker compose logs backend | grep "public code"`.
2. Open `http://localhost:9091/s/<code>` on a phone-width window: title, venue, time in Vietnam time, 2 courts, status; no names.
3. Open `http://localhost:9091/s/WRONGCODE`: friendly not-found.
4. `curl -s http://localhost:9090/api/v1/public/sessions/<code>`: only allowlisted fields.
5. `curl -s http://localhost:9090/api/v1/auth/csrf`: header name + token.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro
