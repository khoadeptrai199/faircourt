# faircourt
pet project chill chill

---

FairCourt helps an office badminton group form fair doubles matches during one play session.
Spec: [FairCourt_Project_Blueprint.md](FairCourt_Project_Blueprint.md). This repository builds the MVP only.

## Status

| Slice (blueprint §17) | State |
|---|---|
| 1. Fixture and domain: Flyway V1, local seed, pure matcher + tests, Docker skeleton | Implemented; verified on Mac only (see Verification log) |
| 2. Public + identity (login, CSRF bootstrap, public page, guest join, `/me`) | Not started |
| 3. Host core loop | Not started |
| 4. Player loop | Not started |
| 5. Polish | Not started |

Only `GET /actuator/health` is reachable right now; every other backend route is denied until slice 2.

## Requirements

Docker with Compose v2 only. Java, Maven and Node do **not** need to be installed on the host; everything builds inside containers.
Targets macOS (Apple Silicon) and Windows WSL. Every pinned base image showed both `linux/amd64` and `linux/arm64` in `docker buildx imagetools inspect` (2026-11-09). See "Verification log" for what has actually been run on which machine.

- **macOS:** Docker Desktop needs permission to read the folder that holds this repo. If it lives under `~/Desktop`, go to System Settings → Privacy & Security → Files & Folders (or Full Disk Access) → Docker, then quit and reopen Docker Desktop. Otherwise bind mounts fail with `mkdir /host_mnt/...: operation not permitted`.
- **WSL:** clone the repo inside the Linux filesystem (for example `~/code/faircourt`), not under `/mnt/c/...`, and run `git` / `docker compose` from inside WSL.

## Start / stop

```bash
cp .env.example .env            # once per machine; never commit .env
docker compose up --build -d    # start
docker compose ps               # all services should become "healthy"
docker compose down             # stop; keeps the database volume
```

| What | URL |
|---|---|
| App (Vite dev server) | http://localhost:5173 |
| Backend health | http://localhost:8080/actuator/health |

Ports are bound to `127.0.0.1` only. PostgreSQL is not published to the host; use `docker compose exec postgres psql -U faircourt faircourt`.

After changing `frontend/package.json`, run `docker compose up --build -d -V` so the container gets fresh `node_modules`.

## Demo data (local profile only)

Created once by the backend when `SPRING_PROFILES_ACTIVE=local`; restarts do not duplicate it.

- Host login: `host@faircourt.local` / `FairCourtDemo!1`
- 8 READY member players `Player 1` … `Player 8`, logins `player1@faircourt.local` … `player8@faircourt.local` (same demo password), bands 1,1,2,2,3,3,4,4
- One OPEN session, 2 courts, random public code. Print it with:

```bash
docker compose logs backend | grep "public code"
```

The public page `/s/<code>` arrives in slice 2. Login is not available yet either.
These credentials are for local/demo use only.

## Tests

```bash
docker compose --profile test run --rm --build backend-test
```

This runs `mvn verify` in a container: matcher unit tests (`MatchSuggesterTest`), a context test, and PostgreSQL integration tests (`DatabaseSchemaIT`).
They use the separate `faircourt_test` database; the integration test refuses to clean any database whose name does not end in `_test`.

Frontend typecheck + production build:

```bash
docker compose run --rm --no-deps frontend npm run build
```

## Formatting

| Part | Tool | Apply | Check |
|---|---|---|---|
| Backend Java | Spotless 3.11.0 + Palantir Java Format 2.103.0 | `docker compose run --rm --build backend-format` | part of the backend test command (`mvn verify` fails if unformatted) |
| Frontend | Prettier 3.9.10 (`frontend/.prettierrc.json`) | `docker compose run --rm --no-deps frontend npm run format` | `docker compose run --rm --no-deps frontend npm run format:check` |

Run both before committing.

## Reset demo data

> **This deletes the local database.**

```bash
docker compose down -v
```

The next `up` recreates the schema, the `faircourt_test` database and the seed.

## Pinned versions (checked 2026-11-09)

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

## Verification log

| Date | Machine | Command / check | Result |
|---|---|---|---|
| 2026-11-09 | Mac, macOS 15.1 arm64, Docker 28.0.4 | `docker compose up --build -d` | all 3 services healthy |
| 2026-11-09 | Mac | `curl localhost:8080/actuator/health` / `curl localhost:5173/` / `curl localhost:8080/api/v1/...` | `UP` / `200` / `403` (also `403` through the Vite `/api` proxy) |
| 2026-11-09 | Mac | Seed checked in `psql` | 9 users, 8 READY members with bands 1,1,2,2,3,3,4,4, 1 OPEN 2-court session |
| 2026-11-09 | Mac | `docker compose down` then `up -d` | data kept, seed reported "already present" |
| 2026-11-09 | Mac | `docker compose --profile test run --rm --build backend-test` | `BUILD SUCCESS`: 13 matcher + 1 context + 4 PostgreSQL IT tests, `spotless:check` passed |
| 2026-11-09 | Mac | `npm run format:check && npm run build` (in frontend container) | passed |
| 2026-11-09 | Mac, emulated `linux/amd64` | build + run frontend image; build backend runtime image and run `java -version` | `x86_64`, build OK; Java 25.0.5 starts |
| — | Windows WSL | everything | **not verified yet** |
| — | any | 375px / desktop visual check | **not verified yet** |

## Known limitations (slice 1)

- Spring Boot logs a "generated security password" for its default in-memory user. No login mechanism is enabled and every route except health is denied, so it is unusable; it goes away when slice 2 adds the real account login.
- The seeded session time is fixed when the seed first runs. After that evening passes, the session stays in the past until demo data is reset.
- Phone (375px) / desktop layouts have not been checked visually yet; the page is only a placeholder.

## Implementation decisions for owner review

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
