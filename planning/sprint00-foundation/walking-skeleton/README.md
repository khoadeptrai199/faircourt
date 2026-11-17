# F00 — Walking skeleton

| Field | Value |
|---|---|
| Sprint | S00 Foundation |
| Status | `DONE` |
| Blueprint | §0, §10, §12, §14, §17 step 1 |
| Depends on | — |
| Waiting for | — |
| Last updated | 2026-11-17 |

This feature was delivered before the planning folder existed (commits `447b491`, `93f654d`, `b07a4b2`, `0b67fb0`), so there are no design/implement/review files. This card records what exists so the board is complete.

## Delivered (repository scan, 2026-11-17)

| Area | Files |
|---|---|
| Docker stack | `compose.yaml` (postgres, backend, frontend; `test` profile `backend-test`; `tools` profile `backend-format`), `compose.backend-dev.yaml`, `backend/Dockerfile` (deps/test/dev/build/runtime stages), `frontend/Dockerfile`, `postgres/init/01-create-test-db.sql` |
| Schema | `backend/src/main/resources/db/migration/V1__initial_schema.sql` (verbatim §12) |
| Seed | `common/LocalDemoSeeder.java`: local profile only, idempotent, one transaction; host + 8 READY members (bands 1,1,2,2,3,3,4,4) + one OPEN 2-court session |
| Matcher | `matching/` package: `MatchSuggester`, `BoardSnapshot`, `Proposal`, `ReasonCode`, `Warning`, `SuggestionResult` |
| Security baseline | `identity/SecurityConfig.java`: health public, all else `denyAll`, CSRF default on, delegating `PasswordEncoder` |
| Tests | `MatchSuggesterTest` (13), `DatabaseSchemaIT` (4), `FaircourtBackendApplicationTests` (1) |
| Frontend | Home page `/` (`features/home/HomePage.tsx`), tokens in `styles/tokens.css` |

## Acceptance criteria

| ID | Criterion | Result | Evidence |
|---|---|---|---|
| AC-01 | Deterministic two-court proposal for the seed | `PASS` | README verification log, Mac 2026-11-09 and WSL 2026-11-12: `BUILD SUCCESS` 13 + 1 + 4 tests |
| AC-02 | No attendance ID reused across simultaneous courts | `PASS` | `simultaneousCourtsNeverReuseAnAttendanceId` (same runs) |
| AC-03 | Seed idempotent across restarts | `PASS` | `DatabaseSchemaIT.seedIsIdempotent`; README log `down` + `up` "already present" (Mac) |
| AC-04 | One-command start, health UP | `PASS` | README log (Mac and WSL) |
| AC-05 | Home page at 375px and desktop | `NOT VERIFIED` | README "Known limitations" |

These results are copied from the README verification log; nothing was re-run when this card was written.

## Carried forward

- AC-05 visual check → F16.
- Mac verification of the 2026-11-12 changes (debug port, dev mode, polling flag, home page) → next time the owner works on the Mac.

## Records moved from README.md (2026-11-17)

The project README was emptied by owner request on 2026-11-17. The sections below are copied unchanged from `README.md` at commit `15c4edf`, so the small implementation decisions, pinned versions and the verification log quoted above stay findable. Later evidence lives in each feature's `implement.md`.

### Pinned versions (checked 2026-11-09)

| Component | Version | Where pinned |
|---|---|---|
| Java runtime (Eclipse Temurin JRE) | 25.0.5_8 (LTS) | `backend/Dockerfile` runtime stage |
| Maven build image | 3.9.16, pinned by digest `sha256:93b8a14e…`; contains Temurin JDK 25.0.4.1 | `backend/Dockerfile` build/test stages |
| Spotless Maven plugin / Palantir Java Format | 3.11.0 / 2.103.0 | `backend/pom.xml` |
| Spring Boot | 4.1.2 | `backend/pom.xml` |
| PostgreSQL | 18.6 (alpine3.24) | `compose.yaml` |
| Node | 24.22.2 (LTS) | `frontend/Dockerfile` |
| React / React DOM | 19.3.1 | `frontend/package.json` + `package-lock.json` |
| Vite | 8.3.7 | same |
| @vitejs/plugin-react | 6.3.0 | same |
| Tailwind CSS (+ @tailwindcss/vite) | 4.3.5 | same |
| TypeScript | 7.0.2 | same |
| @types/react / @types/react-dom | 19.3.0 | same |
| Prettier | 3.9.10 | same |

### Verification log

| Date | Machine | Command / check | Result |
|---|---|---|---|
| 2026-11-09 | Mac, macOS 15.1 arm64, Docker 28.0.4 | `docker compose up --build -d` | all 3 services healthy |
| 2026-11-09 | Mac | `curl localhost:8080/actuator/health` / `curl localhost:5173/` / `curl localhost:8080/api/v1/...` | `UP` / `200` / `403` (also `403` through the Vite `/api` proxy) |
| 2026-11-09 | Mac | Seed checked in `psql` | 9 users, 8 READY members with bands 1,1,2,2,3,3,4,4, 1 OPEN 2-court session |
| 2026-11-09 | Mac | `docker compose down` then `up -d` | data kept, seed reported "already present" |
| 2026-11-09 | Mac | `docker compose --profile test run --rm --build backend-test` | `BUILD SUCCESS`: 13 matcher + 1 context + 4 PostgreSQL IT tests, `spotless:check` passed |
| 2026-11-09 | Mac | `npm run format:check && npm run build` (in frontend container) | passed |
| 2026-11-09 | Mac, emulated `linux/amd64` | build + run frontend image; build backend runtime image and run `java -version` | `x86_64`, build OK; Java 25.0.5 starts |
| 2026-11-12 | Windows WSL: Ubuntu 24.04 `x86_64`, Docker Engine 29.5.3, Compose v5.1.4; repo on `/mnt/d` | `docker compose up --build -d` | all 3 services healthy; health `UP`; `/` 200; `/api/...` 403 (also via Vite proxy) |
| 2026-11-12 | Windows WSL | Flyway + seed checked in `psql` | V1 applied; 9 app tables; 9 users, 1 session; `faircourt_test` exists |
| 2026-11-12 | Windows WSL | `docker compose --profile test run --rm --build backend-test` | `BUILD SUCCESS`: 13 + 1 + 4 tests, `spotless:check` passed |
| 2026-11-12 | Windows WSL | frontend `format:check` + `build` | passed |
| 2026-11-12 | Windows WSL | Vite hot reload on `/mnt/d` | no file-change events without polling; works with `VITE_USE_POLLING=true` |
| 2026-11-12 | Windows WSL | backend dev mode: `touch` a Java file + `mvn compile` in container | DevTools restart in ~7 s, health `UP`; JDWP 5005 reachable from Windows; packaged jar contains no DevTools |
| — | Mac | changes from 2026-11-12 (debug port, dev mode, polling flag, home page) | **not verified yet** |
| — | any | 375px / desktop visual check | **not verified yet** |

### Known limitations (slice 1)

- Spring Boot logs a "generated security password" for its default in-memory user. No login mechanism is enabled and every route except health is denied, so it is unusable; it goes away when slice 2 adds the real account login.
- The seeded session time is fixed when the seed first runs. After that evening passes, the session stays in the past until demo data is reset.
- Phone (375px) / desktop layouts of the home page (`/`) have not been checked visually yet.

### Implementation decisions for owner review

Small decisions not specified by the blueprint, recorded per §0:

- Java package / Maven group: `com.faircourt`.
- Seed session: title "FairCourt demo session", venue "Demo venue (local seed)", starts at the next 18:00 Asia/Ho_Chi_Minh after seeding and lasts 3 hours. Player bands are stored as `SELF_REPORTED` (schema default).
- Public code: 8 characters from `A–Z` + `2–9`, without the look-alike characters 0/O and 1/I.
- Matcher (section 14): "stable UUID order" means UUIDs are compared as strings. If no court is free, the result is `NO_FREE_COURT`, which is checked before `WAITING_FOR_PLAYERS`. Reason tags:
  - `SIMILAR_LEVEL`: band spread ≤ 1.
  - `BALANCED_TEAMS`: team gap = 0.
  - `LONG_WAIT`: the court includes the earliest-available ready player(s), unless everyone became available at the same instant.
- Slice 1 security: only `/actuator/health` is public; all other routes are denied; CSRF stays at the Spring default (enabled).
- Font: system sans (no web font loaded yet).
