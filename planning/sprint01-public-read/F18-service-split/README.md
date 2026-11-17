# F18 — Service split skeleton

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `WAITING_APPROVAL` |
| Blueprint | §0 (one-command local stack, seed), §12 (schema), §14/§17-1 (matcher gate); OD-17 (microservices), OD-18…OD-20 (new session columns) |
| Depends on | F00 |
| Waiting for | All design questions answered 2026-11-17; owner said "do not implement yet". Waiting for the owner's go-ahead to start building |
| Suggested branch | `feature/F18-service-split` |
| Last updated | 2026-11-17 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

The single backend becomes a gateway plus `auth-service`, `player-service` and `playmatch-service`, each with its own schema in the one PostgreSQL container, started by one `docker compose up --build -d` on Mac and WSL. No new user-visible behavior; the matcher and seed keep working.

## Scope

- In: Maven multi-module (`libs/common-web`, `libs/matching`, `services/*`); one Dockerfile with `MODULE` build arg; Compose services with health checks; Flyway per service on its own schema (tables from system-design §4, incl. `match_format`, `min_sets`, price-per-person columns, `attendance.display_name`); seed split per service with matching fixed IDs; existing tests moved and green; schema boundary test; RAM measurement.
- Out: error model and CSRF (F01), any `/api/v1` route (F02+), identity token (F03), `rating-service` (F13), Kafka (later).

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | `docker compose up --build -d` → postgres, gateway, auth-service, player-service, playmatch-service, frontend all healthy | `docker compose ps` on WSL (Mac or `NOT VERIFIED`) |
| AC-02 | Each service's Flyway creates only its own schema and tables; table list matches system-design §4 | per-service schema IT |
| AC-03 | Seed: 1 host account, 8 `READY` member players, 1 `OPEN` doubles session with minimum sets 6 and a price; IDs line up across schemas; re-running the seed changes nothing | seed IT per service + SQL check |
| AC-04 | Existing tests still pass after the move: `MatchSuggesterTest` (all cases) and the DB backstop tests (busy court, player in two unreleased matches) on the `playmatch` schema | `mvn verify` |
| AC-05 | A test per service fails if its SQL or migrations mention another service's schema | boundary test (incl. one deliberate failing sample in the test itself) |
| AC-06 | Only gateway (9090), frontend (9091), JDWP (9092, opt-in) and PostgreSQL (9093, loopback) publish host ports | `docker compose ps` |
| AC-07 | Idle RAM per container recorded | `docker stats --no-stream` (Mac or `NOT VERIFIED`) |
| AC-08 | Pre-commit pipeline green with the updated commands | pipeline output |
| AC-09 | Each service can be started, rebuilt and followed in the logs on its own with the README commands (e.g. `docker compose up -d playmatch-service` starts only PostgreSQL + playmatch-service) | commands run and recorded |
| AC-10 | The backend dev mode is gone (`compose.backend-dev.yaml`, DevTools, Dockerfile `dev` stage) and the README explains the manual rebuild | `git status` + README |

## Open decisions

| ID | Question | Status |
|---|---|---|
| D-1 | Old tables in the `public` schema of existing local databases | Decided: keep |
| D-2 | What happens to the old `V1__initial_schema.sql` file | Decided: move to `backend/legacy/` |
| D-3 | Gateway technology (new dependency) | Decided: Spring Cloud Gateway (version shown before pinning) |
| D-4 | Seed price value and price rules | Decided: per person by gender, range; men 75k–85k, women 65k–75k; optional; minimum sets 1–20 |
| D-5 | Which service gets the JDWP port 9092 | Decided: `playmatch-service`; every service startable on its own |
| D-6 | Dev mode (`compose.backend-dev.yaml`) | Decided: remove it; manual rebuild |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-17 | Feature created in S01 as part of the owner-approved sprint plan rewrite for microservices (OD-17); design written, waiting for approval | Conversation 2026-11-17 |
| 2026-11-17 | Owner answered D-1…D-6 (see design §10) and accepted the architecture (AD-11…AD-15, AD-17, AD-18). Owner: "do not implement yet". Removal of `compose.backend-dev.yaml` approved ("bỏ đi") | Conversation 2026-11-17 |
