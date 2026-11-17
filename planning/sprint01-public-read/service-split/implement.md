# F18 implementation — Service split skeleton

Design: [design.md](design.md) — approved by the owner 2026-11-17 ("làm hết", answering "start F18?"). Branch `feature/service-split`.

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | — | Parent/aggregator `pom.xml`; `libs/matching` via `git mv` (code + `MatchSuggesterTest`); empty `libs/common-web` | `DONE` |
| T02 | AC-02 | `playmatch-service`: move app class, `ClockConfig`, `PublicCodeGenerator`; V1 for schema `playmatch` incl. new columns; Flyway schema settings | `DONE` |
| T03 | AC-02 | `auth-service` and `player-service`: app class, V1 for their schema, health | `DONE` |
| T04 | AC-03 | Split seeders with deterministic IDs; idempotency tests | `DONE` |
| T05 | AC-04 | Move DB backstop tests to `playmatch-service`; per-service `SchemaIT` | `DONE` |
| T06 | AC-05 | Schema boundary test per service | `DONE` |
| T07 | — | `gateway` app shell with health | `DONE` |
| T08 | AC-01, AC-06, AC-09 | Dockerfile `ARG MODULE`; `compose.yaml` services, health checks, ports, services depend on PostgreSQL only; frontend proxy → gateway; `backend-test`/`backend-format` on the reactor | `DONE` |
| T08b | AC-10 | Remove dev mode: `git rm compose.backend-dev.yaml`, DevTools dependency, Dockerfile `dev` stage, `.env.example` comment lines (approved 2026-11-17) | `DONE` (`.env.example` had no dev-mode line, so it is unchanged) |
| T09 | D-2 | Old V1 file per D-2 | `DONE` (`git mv` to `backend/legacy/`; the note is in the README "Reset demo data" section) |
| T10 | AC-07 | `docker stats` RAM table in README | `DONE` (Mac only; recorded in this file, README is empty by owner request) |
| T11 | AC-08, AC-09 | README: "work with one service" commands (design §6), rebuild instead of dev mode, test commands; run each command once and record; pipeline run | `DONE` (Mac only; commands recorded in design §6 and this file, README is empty by owner request) |

## Files

| File | Added / changed | Why |
|---|---|---|
| `backend/pom.xml` | changed | Parent + aggregator, `<packaging>pom</packaging>`, 6 modules; Spotless + failsafe for all modules; DevTools removed |
| `backend/libs/common-web/pom.xml` | added | Empty module (F01 fills it) |
| `backend/libs/matching/**` | moved + pom added | Matcher and `MatchSuggesterTest` unchanged (`git mv`) |
| `backend/services/gateway/**` | added | App class, deny-all `SecurityConfig`, `application.properties`, `GatewayHealthIT` |
| `backend/services/auth-service/**` | added | App class, `SecurityConfig`, `PasswordConfig`, `LocalDemoSeeder`, V1 (`auth`), `application.properties`, `application-local.properties` (moved), `AuthSchemaIT`, `SchemaBoundaryTest` |
| `backend/services/player-service/**` | added | App class, `SecurityConfig`, `LocalDemoSeeder`, V1 (`player`), `application.properties`, `PlayerSchemaIT`, `SchemaBoundaryTest` |
| `backend/services/playmatch-service/**` | moved + added | App class, `ClockConfig`, `PublicCodeGenerator` moved; `SecurityConfig` and `LocalDemoSeeder` rewritten; V1 (`playmatch`); `PlaymatchSchemaIT` (from `DatabaseSchemaIT`), `PlaymatchServiceApplicationTests` (moved), `SchemaBoundaryTest` |
| `backend/legacy/V1__initial_schema.sql` | moved | D-2 |
| `backend/Dockerfile` | changed | `ARG MODULE`; deps stage copies all poms; `dev` stage removed |
| `compose.yaml` | changed | `backend` → `gateway` + 3 services; frontend depends on and proxies to `gateway` |
| `compose.backend-dev.yaml` | **removed** | D-6, owner-approved 2026-11-17 |
| `frontend/vite.config.ts` | changed | Comment only (`backend:8080` → `gateway:8080`) |
| `.gitignore` | changed | `backend/target/` → `backend/**/target/` (every module has its own `target/`) |
| `README.md` | emptied | Owner request 2026-11-17: README stays an empty file for now (CLAUDE.md "Owner decisions") |

Deleted files: `compose.backend-dev.yaml` only (owner approved 2026-11-17). Everything else moved with `git mv`. Git shows `playmatch/security/SecurityConfig.java` and `playmatch/seed/LocalDemoSeeder.java` as added + deleted (not a rename) because their content was rewritten.

## Commands executed

All on the Mac (macOS 15.1, `arm64`, Docker 28.0.4, Compose v2.34.0-desktop.1), 2026-11-17.

| Command | Result |
|---|---|
| `docker compose config --services` | 6 services parsed; published ports 9090, 9091, 9092, 9093 only |
| `docker compose run --rm --build backend-format` | deps stage built (`dependency:go-offline` over the reactor, 319 s first time); Spotless reformatted 3 new test files |
| `docker compose --profile test run --rm --build backend-test` (1st) | `BUILD FAILURE`: `PlaymatchSchemaIT` expected `Short` for SMALLINT columns, the driver returns `Integer` (test bug); fixed |
| `docker compose --profile test run --rm --build backend-test` (2nd) | `BUILD SUCCESS`, 7 modules: `MatchSuggesterTest` 13, `GatewayHealthIT` 2, auth `SchemaBoundaryTest` 2 + `AuthSchemaIT` 3, player 2 + `PlayerSchemaIT` 3, playmatch context 1 + `SchemaBoundaryTest` 2 + `PlaymatchSchemaIT` 8; `spotless:check` passed on every module |
| `docker compose up --build -d`, `docker compose ps` | postgres, gateway, auth-service, player-service, playmatch-service, frontend all `healthy` |
| `curl localhost:9090/actuator/health` · `curl localhost:9090/api/v1/x` · `curl localhost:9091/` | `{"status":"UP"}` · `403` · `200` |
| `docker compose logs … \| grep 'demo seed'` | auth / player / playmatch `CREATED`; public code printed by playmatch-service |
| `psql`: tables in `auth`, `player`, `playmatch` | exactly the design §4 lists + `flyway_schema_history` in each |
| `psql`: cross-schema ID check | 8 attendance ↔ player profiles (same `display_name`), 8 profiles ↔ accounts, 1 session ↔ host account |
| `docker stats --no-stream` | gateway 250 MiB, auth 295, player 306, playmatch 307, frontend 262, postgres 119 |
| `docker compose down`; `docker compose up -d playmatch-service`; `docker compose ps` | only postgres + playmatch-service running |
| `docker compose up -d --build playmatch-service` | exit 0 |
| `docker compose up -d`; `docker compose stop playmatch-service`; `ps`; `start` | others stayed healthy; playmatch-service restarted |
| `docker compose logs -f --tail 1 gateway playmatch-service` (3 s) | lines from both services in one stream |
| Seed after 3 restarts (`psql` counts) | 9 users / 8 profiles / 1 session / 8 attendance; all three seeders "already present"; same public code |
| `docker compose run --rm --no-deps frontend npm run format` / `format:check` / `build` | passed |
| `git diff --cached --name-only` checked for `.env` / `.idea/` | none staged |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | `docker compose ps` | PASS (Mac) · NOT VERIFIED (WSL) | 6 containers healthy (see above). WSL not run; its `.env` needs the `COMPOSE_FILE` line removed first |
| AC-02 | `<Service>SchemaIT` | PASS | `migrationCreatesOnly…` in `AuthSchemaIT`, `PlayerSchemaIT`, `PlaymatchSchemaIT`; same lists in `psql` on the dev DB |
| AC-03 | Seed ITs + manual SQL | PASS | `seedIsIdempotent` + seed content tests in all three ITs; cross-schema `psql` check 8/8/1; counts unchanged after restarts |
| AC-04 | `mvn verify` | PASS | `MatchSuggesterTest` 13/13; `databaseRejectsSecondBusyMatchOnSameCourt`, `databaseRejectsPlayerInTwoUnreleasedMatches` in `PlaymatchSchemaIT` |
| AC-05 | Boundary test | PASS | `SchemaBoundaryTest` per DB service; `checkRecognisesAQueryOnAnotherServicesSchemaButNotOnItsOwn` covers the deliberate failing sample |
| AC-06 | `docker compose ps` ports | PASS (Mac) | gateway 9090, frontend 9091, playmatch JDWP 9092, postgres 9093, all on `127.0.0.1`; services expose only `8080/tcp` internally |
| AC-07 | `docker stats` | PASS (Mac) · NOT VERIFIED (WSL) | "Commands executed" above (gateway 250, auth 295, player 306, playmatch 307, frontend 262, postgres 119 MiB) |
| AC-08 | Pre-commit pipeline | PASS (Mac) | spotless apply → prettier write → `mvn verify` (incl. `spotless:check`) → `format:check` + `build` → no `.env`/`.idea` staged |
| AC-09 | One-service commands run | PASS (Mac) | see commands above |
| AC-10 | Dev mode removed + README | PASS | `compose.backend-dev.yaml` removed; DevTools gone from poms; Dockerfile has no `dev` stage; rebuild commands in design §6. README part waived: README is empty by owner request |

## Deviations from the design

- **Schema boundary test scope.** It flags schema-qualified table names (`auth.app_user`), `CREATE/DROP/ALTER/SET SCHEMA`, `search_path` and `schema(s)=` settings for other schemas. It does not flag the bare words `auth.`/`player.`, because Java code such as `auth -> auth.requestMatchers(...)` would be a false positive. The helper is copied into each service; F01 can move it into `common-web` test support.
- **`PasswordEncoder` only in `auth-service`** (the design said `SecurityConfig` is "copied into each service"; the password part is needed only where passwords are stored).
- **`application-local.properties`** moved to `auth-service`, the only service that reads the seed email/password.
- **Test names:** `<Service>SchemaIT` also holds the seed and DB backstop tests (the old `DatabaseSchemaIT` was one class too). `PlaymatchServiceApplicationTests` kept its old style (moved, not refactored).
- **`libs/matching` test dependencies:** declared `junit-jupiter` and `assertj-core` (test scope, versions from the Boot BOM). These are the same libraries the test already used through the Boot test starters; no new artifact.
- **`.gitignore`** pattern widened to `backend/**/target/` (not listed in the design; needed by the module layout).
- Flyway/Hikari property names were checked by running, not against the Spring Boot 4.1 reference: `current_schema()` is the service schema in all three ITs.
- **README emptied** (owner request 2026-11-17, after implementation). The README text written for AC-07/AC-10/T11 (services table, RAM, debugging, one-service commands, verification log) was removed with it; the same facts are kept in this file and design §6.
