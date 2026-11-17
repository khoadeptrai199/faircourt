# F18 design — Service split skeleton

Status: `APPROVED` (2026-11-17, owner: "làm hết")

## 1. Summary

Turn today's single backend (`backend/src/...`, one jar, one Flyway history in the `public` schema) into the layout of [system-design §2–§4](../../architecture/system-design.md):

```text
backend/pom.xml                 parent + aggregator
backend/Dockerfile              build arg MODULE
backend/libs/common-web/        (empty shell now; F01 fills it)
backend/libs/matching/          today's com.faircourt.matching + MatchSuggesterTest, moved with git mv
backend/services/gateway/       Spring Boot app, health only (routes come in F01/F02)
backend/services/auth-service/      schema auth:      app_user, guest_access
backend/services/player-service/    schema player:    player_profile, skill_assessment
backend/services/playmatch-service/ schema playmatch: play_session, attendance, play_match, match_player
```

No `/api/v1` route, no identity, no user-visible change. The point is that every later feature lands in the right service from day one.

## 2. Blueprint and owner references

- §0: the stack starts with one Compose command; seed = 1 host account + 8 `READY` member players + 1 session.
- §12: tables, constraints and partial unique indexes (copied, split by owner, cross-schema FKs removed).
- §17 step 1 gate: deterministic two-court proposal, no duplicate players. F18 must keep it green.
- OD-17 microservices; OD-18 price shown; OD-19 minimum sets per session (default 6); OD-20 singles or doubles per session; owner 2026-11-17: one PostgreSQL container, one schema per service, one shared DB user, one repo with one folder per service.

## 3. API contract

None. Each service exposes only `/actuator/health`. The gateway has no routes yet (F01).

## 4. Data

### Migrations (new files, one `V1__…` per service on its own schema)

| Service | Flyway schema | Tables | Differences from the blueprint V1 |
|---|---|---|---|
| auth-service | `auth` | `app_user`, `guest_access` | `guest_access` FK to `attendance` removed; `session_id`, `attendance_id` stay as UUID columns |
| player-service | `player` | `player_profile`, `skill_assessment` | FK `player_profile.user_id → app_user` removed; FKs from `skill_assessment` to `play_session` and `app_user` removed |
| playmatch-service | `playmatch` | `play_session`, `attendance`, `play_match`, `match_player` | FK `host_user_id → app_user` and `attendance.player_id → player_profile` removed; **new columns** below |

New columns in `playmatch` (owner decisions OD-18…OD-20, D-4 answered 2026-11-17):

```sql
-- play_session
match_format VARCHAR(7) NOT NULL DEFAULT 'DOUBLES' CHECK (match_format IN ('SINGLES','DOUBLES')),
min_sets     SMALLINT   NOT NULL DEFAULT 6 CHECK (min_sets BETWEEN 1 AND 20),
-- price per person by gender, as a range; all four NULL = no price shown
price_male_min_vnd   INTEGER CHECK (price_male_min_vnd >= 0),
price_male_max_vnd   INTEGER CHECK (price_male_max_vnd >= 0),
price_female_min_vnd INTEGER CHECK (price_female_min_vnd >= 0),
price_female_max_vnd INTEGER CHECK (price_female_max_vnd >= 0),
CHECK (price_male_min_vnd   IS NULL OR price_male_max_vnd   IS NULL OR price_male_min_vnd   <= price_male_max_vnd),
CHECK (price_female_min_vnd IS NULL OR price_female_max_vnd IS NULL OR price_female_min_vnd <= price_female_max_vnd),
-- attendance
display_name VARCHAR(80) NOT NULL CHECK (length(trim(display_name)) > 0)  -- copied from player-service at join
```

All other constraints and indexes from §12 stay as they are inside each schema (incl. `ux_busy_court`, `ux_player_in_unreleased_match`, `ux_active_guest_access`). `match_feedback` is not created now; it goes to `rating-service` in F13.

Flyway settings per service: `spring.flyway.schemas=<schema>` (Flyway creates the schema and keeps its history table there) and `spring.jpa`/JDBC default schema set to the same name. Property names are checked against the Spring Boot 4.1 reference during implementation.

### Seed (local profile only, per service)

The seed is split so each service writes only its own tables. IDs must line up across schemas, so all seed IDs are derived the same way in every service: `UUID.nameUUIDFromBytes("faircourt-demo:<kind>:<n>")` (deterministic, no shared code). Each seeder is idempotent (checks its own first row).

| Service | Seeds |
|---|---|
| auth-service | host account + 8 member accounts (emails/password from `.env` as today) |
| player-service | 8 `MEMBER` profiles, bands 1,1,2,2,3,3,4,4 (as today) |
| playmatch-service | 1 `OPEN` session, 2 courts, `DOUBLES`, `min_sets` 6, price men 75,000–85,000 VND and women 65,000–75,000 VND (owner 2026-11-17); 8 `READY` attendance rows with the same display names; logs the public code (as today) |

### Existing local databases (D-1)

The Mac and WSL databases already contain the blueprint V1 tables in the `public` schema. After F18 nothing reads them. Owner decision 2026-11-17: keep them (no volume reset).

Gender is a property of the price list only. Players' gender is **not** stored or asked anywhere in F18 (privacy rule: store only necessary personal data).

## 5. Domain rules

None new. `match_format`, `min_sets` and the four price columns are stored and seeded only; rules that use them arrive in F02 (display), F06/F08 (host board, create session), F09/F10 (singles).

## 6. Components

| Item | New / moved / changed | Notes |
|---|---|---|
| `backend/pom.xml` | changed | Becomes parent + aggregator with `<modules>`; Spring Boot 4.1.2 parent kept; Spotless and failsafe config moved to the parent so every module uses them |
| `libs/matching/**` | moved (`git mv`) | `com.faircourt.matching.*` and `MatchSuggesterTest`; plain jar, no Spring |
| `libs/common-web/` | new | Empty module now (F01 fills it) |
| `services/playmatch-service/**` | moved + new | `ClockConfig`, `PublicCodeGenerator`, application class; split seeder; V1 for `playmatch`; DB backstop tests from `DatabaseSchemaIT` |
| `services/auth-service/**` | new | Application class, V1 for `auth`, seeder, health |
| `services/player-service/**` | new | Application class, V1 for `player`, seeder, health |
| `services/gateway/**` | new | Application class + health only; gateway dependency per D-3 is added in F01 |
| `SecurityConfig` | moved to each service | Today's rule (health public, everything else denied) copied into each service; F01 replaces it |
| `backend/Dockerfile` | changed | Stages `deps` / `test` / `build` / `runtime` kept, `dev` stage removed (D-6); `ARG MODULE`; the runtime copies `services/${MODULE}/target/*.jar` |
| `compose.yaml` | changed | `backend` replaced by `gateway` (host 9090) + 3 services (no host ports), each with a health check. Services `depends_on` PostgreSQL only; the gateway does **not** depend on the services, so each one can be started alone. Frontend proxy target → `http://gateway:8080`; `backend-test` and `backend-format` run the whole reactor; JDWP 9092 on `playmatch-service` (D-5) |
| `compose.backend-dev.yaml` | **removed** (D-6, owner 2026-11-17: "drop it, I rebuild myself") | `git rm`; the `spring-boot-devtools` dependency and the `.env` lines that enable it are removed too |
| `.env.example` | unchanged | Same DB user, passwords, seed accounts |
| Schema boundary test | new, per service | Scans `src/main/resources/db/migration/**` and `src/main/java/**` for the other services' schema names (`auth.`, `player.`, `playmatch.`, `rating.`) |
| README | changed | Service list, RAM table, and the "work with one service" commands below; dev-mode section replaced by the rebuild commands; version table unchanged until the gateway dependency is added in F01 |

No new dependency in F18 (the gateway dependency, Spring Cloud Gateway per D-3, is added in F01).

### Working with one service (goes into the README; owner request 2026-11-17)

The owner edits and compiles in IntelliJ, then rebuilds and restarts by hand. Commands run in WSL (or the Mac terminal) from the repo root:

| I want to… | Command |
|---|---|
| Start everything | `docker compose up --build -d` |
| Start only one service (PostgreSQL starts with it) | `docker compose up -d playmatch-service` |
| Rebuild and restart one service after a code change | `docker compose up -d --build playmatch-service` |
| Follow the live log of one service | `docker compose logs -f playmatch-service` |
| Follow several services in one window | `docker compose logs -f gateway playmatch-service` |
| Find one request across services | `docker compose logs gateway playmatch-service \| grep <requestId>` |
| Stop one service (others keep running) | `docker compose stop playmatch-service` |
| See what is running and healthy | `docker compose ps` |

Service names: `gateway`, `auth-service`, `player-service`, `playmatch-service` (`rating-service` from S05). A rebuild compiles only what Docker needs for that image; the Maven cache volume keeps it fast after the first build (time not measured yet).

Debugging: `BACKEND_DEBUG_OPTS` in `.env` keeps working, now for `playmatch-service` on `localhost:9092` (IntelliJ "Remote JVM Debug" unchanged).

## 7. Frontend

Only `API_PROXY_TARGET` in Compose changes (backend → gateway). No code change.

## 8. Security and privacy checklist

- [x] Services publish no host ports; only the gateway, frontend, opt-in debug and loopback PostgreSQL do
- [x] Every service still denies everything except health (no route opened)
- [x] No secrets added; same `.env` values
- [x] Seed stays in the `local` profile only

## 9. Test plan

| TC | Covers | Case | Layer |
|---|---|---|---|
| TC-01 | AC-01, AC-06 | `docker compose up --build -d`; `docker compose ps` shows 6 healthy containers and only the allowed host ports | manual command, recorded |
| TC-02 | AC-02 | Per service: after migrate, `information_schema.tables` for its schema equals the expected table list | `<Service>SchemaIT` |
| TC-03 | AC-03 | Per service: seeder twice → same row counts; cross-check SQL (run by hand in DBeaver/psql) that attendance `player_id`s exist in `player.player_profile` | IT + manual SQL |
| TC-04 | AC-04 | `MatchSuggesterTest` all cases; busy-court and player-in-two-matches backstops on `playmatch` | `mvn verify` |
| TC-05 | AC-05 | Boundary test passes on real sources and fails on an in-test sample string `select * from auth.app_user` | unit test |
| TC-06 | AC-07 | `docker stats --no-stream` after start, recorded | manual |
| TC-07 | AC-08 | Pipeline with updated commands | pipeline |

## 10. Decisions

All answered by the owner on 2026-11-17:

| ID | Question | Owner answer |
|---|---|---|
| D-1 | Old tables in the `public` schema | Keep them (recommendation accepted, "no opinion") |
| D-2 | Old `V1__initial_schema.sql` | `git mv` to `backend/legacy/` (recommendation accepted, "no opinion") |
| D-3 | Gateway technology | Spring Cloud Gateway; servlet variant as recommended; version shown to the owner before pinning (F01) |
| D-4 | Price and minimum sets | Price per person by gender as a range; demo men 75k–85k, women 65k–75k; optional; minimum sets 1–20, default 6 |
| D-5 | Debug port | 9092 on `playmatch-service`; every service can be started, rebuilt and logged on its own (commands in §6) |
| D-6 | Dev mode | Removed; the owner rebuilds by hand with the commands in §6 |

The options considered are kept below for the record.

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Old V1 tables in the `public` schema of the existing Mac/WSL databases | (a) leave them; unused and harmless · (b) owner resets the volume (`docker compose down -v`, deletes local demo data; needs explicit consent each time) | (a) now; reset later only if you want a clean DB |
| D-2 | Old `backend/src/main/resources/db/migration/V1__initial_schema.sql` | (a) `git mv` to `backend/legacy/` with a one-line note; not on any Flyway path · (b) delete it (git history keeps it; needs consent) | (a) |
| D-3 | Gateway technology (new dependency, added in F01) | (a) Spring Cloud Gateway, servlet (Web MVC) variant: same Spring Security servlet setup as the services · (b) Spring Cloud Gateway, reactive (WebFlux) variant: the classic one, but a different programming model to learn · (c) hand-written proxy with Spring MVC + `RestClient`: no new dependency, more code | (a), **if** a Spring Cloud release that supports Boot 4.1.2 exists; the exact version is checked on the official Spring Cloud compatibility page in F01 and shown to you before it is pinned. If none exists, (c) |
| D-4 | Price and minimum-sets rules | Seed price value (e.g. total VND for the evening)? Is price optional (NULL = not shown)? Upper limit for `min_sets`? | You give the seed price; price optional; `min_sets` 1–20 |
| D-5 | JDWP debug port 9092 | (a) on `playmatch-service` (most logic) · (b) selectable per machine with a `.env` variable | (a); other services get ports from 9094 when needed, with your OK |
| D-6 | Dev mode (`compose.backend-dev.yaml`, `spring-boot:run` + DevTools) with several services | (a) dev mode for `playmatch-service` only for now · (b) for every service (more RAM: one Maven JVM + one app JVM per service) | (a) |

## 11. Out of scope

- Error model, request ID, CSRF, route table → F01
- Any `/api/v1` route → F02+
- Identity token, login, guest cookie → F03, F04
- `rating-service` → F13
- Kafka → later (AD-17)
