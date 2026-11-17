# F18 implementation — Service split skeleton

Design: [design.md](design.md) — status `WAITING_APPROVAL`. No task starts before approval.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | — | Parent/aggregator `pom.xml`; `libs/matching` via `git mv` (code + `MatchSuggesterTest`); empty `libs/common-web` | `TODO` |
| T02 | AC-02 | `playmatch-service`: move app class, `ClockConfig`, `PublicCodeGenerator`; V1 for schema `playmatch` incl. new columns; Flyway schema settings | `TODO` |
| T03 | AC-02 | `auth-service` and `player-service`: app class, V1 for their schema, health | `TODO` |
| T04 | AC-03 | Split seeders with deterministic IDs; idempotency tests | `TODO` |
| T05 | AC-04 | Move DB backstop tests to `playmatch-service`; per-service `SchemaIT` | `TODO` |
| T06 | AC-05 | Schema boundary test per service | `TODO` |
| T07 | — | `gateway` app shell with health | `TODO` |
| T08 | AC-01, AC-06, AC-09 | Dockerfile `ARG MODULE`; `compose.yaml` services, health checks, ports, services depend on PostgreSQL only; frontend proxy → gateway; `backend-test`/`backend-format` on the reactor | `TODO` |
| T08b | AC-10 | Remove dev mode: `git rm compose.backend-dev.yaml`, DevTools dependency, Dockerfile `dev` stage, `.env.example` comment lines (approved 2026-11-17) | `TODO` |
| T09 | D-2 | Old V1 file per D-2 | `TODO` |
| T10 | AC-07 | `docker stats` RAM table in README | `TODO` |
| T11 | AC-08, AC-09 | README: "work with one service" commands (design §6), rebuild instead of dev mode, test commands; run each command once and record; pipeline run | `TODO` |

## Files

| File | Added / changed | Why |
|---|---|---|

Deleted files: `compose.backend-dev.yaml` (owner approved 2026-11-17). Everything else moves with `git mv`; the old V1 file goes to `backend/legacy/`.

## Commands executed

| Date | Machine | Command | Result |
|---|---|---|---|

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `docker compose ps` | | |
| AC-02 | `<Service>SchemaIT` | | |
| AC-03 | Seed ITs + manual SQL | | |
| AC-04 | `mvn verify` | | |
| AC-05 | Boundary test | | |
| AC-06 | `docker compose ps` ports | | |
| AC-07 | `docker stats` | | |
| AC-08 | Pre-commit pipeline | | |
| AC-09 | One-service commands run | | |
| AC-10 | Dev mode removed + README | | |

## Deviations from the design

- none yet
