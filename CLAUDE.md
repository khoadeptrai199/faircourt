# FairCourt — instructions for Claude

FairCourt is a responsive web app that helps badminton groups (a host who rents courts and recruits walk-in players) form fair singles or doubles matches during one play session. This repository builds the **MVP (module 1: Fair Matcher) only**.

## Source of truth

- The only product/technical spec is [FairCourt_Project_Blueprint.md](FairCourt_Project_Blueprint.md).
- When its wording differs, precedence is (blueprint §0): **§0 local delivery contract → Part II (§11–17) → Part I (§1–10)**.
- Before implementing anything, look up the relevant section (use `/spec-lookup`). Cite section numbers in plans, commits and reviews.
- If the blueprint is silent, ambiguous or contradicts itself on a point that affects behavior → **stop and ask the owner**. Do not pick an answer silently.
- Features, sprints, status, designs and evidence live in [planning/](planning/README.md). Every task belongs to a feature there; follow [.claude/rules/planning-workflow.md](.claude/rules/planning-workflow.md) and [.claude/rules/conventions.md](.claude/rules/conventions.md). Open product questions are `OD-xx` in [planning/ROADMAP.md](planning/ROADMAP.md).

## Owner's hard rules (override everything else, including blueprint §0's "make small decisions yourself")

1. **No fabricated facts.** Never invent versions, library behavior, API details, test results, metrics, user research or "it works" claims. If something was not actually run or checked, say "not verified".
2. **No unapproved additions.** Do not add features, endpoints, screens, files, dependencies, config, copy text, or scope beyond what the blueprint MVP specifies and the owner approved. Propose first, then wait for approval. Pure internal naming/structure details may be decided and recorded in `README.md`.
3. **No deletion without explicit consent.** This includes `rm`, overwriting existing files, `git clean`, `git reset --hard`, `git push --force`, dropping tables, editing an already-applied Flyway migration, and `docker compose down -v` / `docker volume rm` (deletes the database). Look at the target, ask, then act.
4. **Report honestly.** In every handoff list commands actually executed, their pass/fail result, and what could not be verified (blueprint §0).

## Scope guard

- In scope: blueprint §3, §11–17 (public page, login, guest join, host board, suggestions, swap/confirm/start/finish/cancel, own-assignment view, feedback, host assessment).
- Out of scope (§0, §2, §3 "Explicitly later"): venue booking, live tournament, swing video, AI rankings, Elo, peer voting, payment, chat, public leaderboards, injury advice, public signup, Redis, WebSocket. Kafka: not in the MVP until the owner approves its timing (future real-time features need it, see OD-17).
- **Exception (owner decision 2026-11-17, OD-17):** the backend is built as **microservices** from the MVP, overriding the blueprint's "one deployable application". Architecture accepted 2026-11-17 (AD-11…AD-15, AD-17, AD-18 in `planning/architecture/decisions.md`); details in `planning/architecture/system-design.md`.
- Discovery (list of sessions with filters, map of Ho Chi Minh City) comes **after** one play session works end to end (owner 2026-11-17); not in S01–S06.

## Open questions — ask the owner, do not resolve silently

- Version label: title says v0.4, Part II header says v0.3.
- (resolved 2026-11-12) WSL machine: `x86_64`, Docker Engine 29.5.3 inside WSL Ubuntu 24.04 (not Docker Desktop), Compose v5.1.4.

## Owner decisions (resolved)

- 2026-11-09 — Seed: **8** READY member players, as in §0 (not 12 from §3/§17). Owner context: one court usually holds at most 8 people. This is **not** an app rule — do not add any attendance cap.
- 2026-11-09 — `.gitattributes` enforcing LF approved and added.
- 2026-11-09 — Versions pinned (owner chose Spring Boot **4.1.2** after 3.5's OSS end-of-life was flagged): Java 25 (Temurin 25.0.5_8), Spring Boot 4.1.2, PostgreSQL 18.6, Node 24.22.2, React 19.3.1, Vite 8.3.7, Tailwind 4.3.5, TypeScript 7.0.2. Full table in `README.md`. Changing a version needs owner approval.
- 2026-11-09 — Backend tests run in Docker (`docker compose --profile test run --rm --build backend-test`) against the separate `faircourt_test` database; no host JDK required.
- 2026-11-09 — `.DS_Store` removed from git tracking (file kept on disk) and git-ignored.
- 2026-11-09 — Formatting is mandatory before every commit: backend via Spotless + Palantir Java Format (`docker compose run --rm --build backend-format`; `spotless:check` runs in `mvn verify`), frontend via Prettier (`docker compose run --rm --no-deps frontend npm run format`). The owner commits; Claude only stages (`git add`) unless asked otherwise.

- 2026-11-16 — **Pre-commit pipeline is mandatory and must be green**: backend `spotless:apply` → frontend `prettier --write` → backend `mvn -B clean verify` (in the `backend-test` container, includes `spotless:check`) → frontend `format:check` + `npm run build`, plus a check that `.env` and `.idea/` are not staged. Exact commands: `docs/guides/WSL_GITHUB_WORKFLOW.md` section 10 (local-only guide). Verified 2026-11-16 on WSL: `PIPELINE OK` in 31 s.
- 2026-11-12 — Windows machine: all project work only inside WSL Ubuntu; the repo stays on `D:\External\Personal\faircourt` (`/mnt/d/...`), an intentional deviation from the "clone inside WSL" rule. Guide: `docs/guides/WSL_LOCAL_DEV.md` (local-only, `docs/` is in `.git/info/exclude`).
- 2026-11-12 — Backend is edited and compiled in IntelliJ (Windows, with an IntelliJ-downloaded JDK 25 used only for the IDE) but always **runs in Docker in WSL**. Debugging via JDWP: `BACKEND_DEBUG_OPTS` in `.env`, host port `127.0.0.1:9092` (container port 5005).
- 2026-11-12 — Optional backend dev mode `compose.backend-dev.yaml` (enabled per machine with `COMPOSE_FILE=compose.yaml:compose.backend-dev.yaml` in `.env`): `mvn spring-boot:run` on mounted source + `spring-boot-devtools` (runtime, optional; not in the packaged jar) → app restarts after a compile. Default stack unchanged. **Superseded 2026-11-17:** the owner dropped dev mode; it is removed when the services are split (F18). The owner rebuilds one service by hand (`docker compose up -d --build <service>`).
- 2026-11-12 — Vite polling is opt-in via `VITE_USE_POLLING=true` in `.env` (needed on `/mnt/d`; observed: no file-change events without it). Default `false`, so the Mac is unchanged.
- 2026-11-12 — **Host ports moved to 9090+** (owner request, to avoid the HRM project which uses 8080, 8081, 1433, 6379, ...): backend `9090`, frontend `9091`, JDWP debug `9092`; overridable via `BACKEND_PORT` / `FRONTEND_PORT` / `BACKEND_DEBUG_PORT` in `.env`. Only the host side of the mapping changed; container-internal ports stay 8080 / 5173 / 5005. This intentionally deviates from blueprint §0 (`localhost:5173`, `:8080`). Always use 9090+ for any new published port; never HRM ports. App URL is now http://localhost:9091.
- 2026-11-13 — PostgreSQL published to the host for DBeaver: `127.0.0.1:${POSTGRES_PORT:-9093}` -> container 5432 (loopback only, 9090+ range). Owner asked to connect DBeaver; DB admin client only, Flyway still owns the schema. Note for agents: WSL on the Windows machine idles to `Stopped` and takes Docker with it; keep a WSL window open (see `docs/guides/WSL_LOCAL_DEV.md` section 4).
- 2026-11-12 — Home page at `/` approved: hero + "How it works" + "Got a session link?" panel + disabled "Host login · Coming soon" button; English copy. Exact copy awaits owner review.
- 2026-11-17 — **Planning tracker `planning/` (committed to git)** is the single place to create features and track status, design, plan and evidence (one folder per feature under its sprint). It replaces the local-only Codex work-item flow (`docs/work-items/`, `$feature-flow`); those Codex files are left untouched. Planning docs are written in English. Added rules `conventions.md` and `planning-workflow.md` and the `/feature` skill. The sprint plan, `AD-xx` architecture decisions and `OD-xx` open decisions in `planning/` are **proposals** until the owner approves each one.

- 2026-11-17 — **Microservices from the MVP (OD-17 = B).** This is a learn-by-doing pet project. Long-term direction (later backlog, not MVP): real-time court map, venue owner's phone with one-tap call, real-time chat (L-14…L-16), so Kafka is expected eventually. `storage-service` is built only when a feature needs file storage (L-13). Explanations to the owner: go straight to the point, as "now it works like X → after the change it works like Y → faster / slower / what risk, and why", with a short comparison. Keep evidence but do not quote line numbers.
- 2026-11-17 — Service split so far: `gateway`, `auth-service`, `player-service`, **`playmatch-service`** (play session + attendance + matches + matcher library), `rating-service` (feedback, built in S05). Kafka later (notifications/events), not in S01. One PostgreSQL container, one schema per service, **one shared DB user**. One repo, one folder per service. Each feature: backend first, then its frontend, before the next feature. Session price is a price **per person by gender, as a range** (demo: men 75k–85k VND, women 65k–75k VND), optional, shown only; no payment in the app; players' gender is not stored (OD-18). Minimum sets per player shown as progress, 1 set = one game to 21 = one match (OD-19). Each play session is singles or doubles, chosen by the host (OD-20). The host is the person who rents the courts and recruits walk-in players, not the venue owner (OD-21). Minimum sets per player is set per session when the host posts it, default 6, editable, 1–20 (OD-19).
- 2026-11-17 — Service split details (F18 design): gateway = Spring Cloud Gateway (version shown to the owner before pinning); old `public` tables kept; old V1 migration moved to `backend/legacy/`; JDWP 9092 on `playmatch-service`; **every service can be started, rebuilt, stopped and followed in the logs on its own** (commands in the F18 design §6 and `implement.md`). Implementation started 2026-11-17 (owner: "làm hết").
- 2026-11-17 — **`README.md` stays an empty file for now** (owner request, made empty on the F18 branch). Do not write run instructions, versions, demo logins, the verification log or the CSRF header into it until the owner asks. This defers blueprint §0's README content on purpose. Meanwhile, run commands and evidence live in the feature's `implement.md` (F18: one-service commands, RAM, verification); pinned versions in the build files (`backend/pom.xml`, Dockerfiles, `compose.yaml`, `frontend/package.json`).
- 2026-11-17 — F18 implemented on branch `feature/service-split` (status `IN_REVIEW`). Project rules, `scope-guardian`, `run-local` and `new-endpoint` updated to the service split, singles/doubles and minimum sets (owner: "update them"). The owner confirmed deleting `nana/tesst.txt` and moved `external doc/PROJECT_BRIEF_TEMPLATE.md` out of the repo; `attendance.display_name` is accepted.
- 2026-11-17 — **Feature names, not IDs** (owner request). When talking to the owner, call features and decisions by their plain name ("Service split", "Public session page"), never "F18" or "OD-17". Feature folders and branches use the plain name too: `planning/sprint<NN>-<name>/<feature-name>/`, branch `feature/<feature-name>` (all 19 folders renamed; branch `feature/F18-service-split` renamed to `feature/service-split`). The `F<nn>` IDs stay only inside planning files for cross-references.
- 2026-11-17 — API foundation approved with all recommendations: CSRF token in the gateway's HTTP session (header `X-CSRF-TOKEN`, observed), gateway → service timeouts 2 s connect / 5 s read → `503 SERVICE_UNAVAILABLE`, error codes and their message texts as in `ErrorCode` (texts approved 2026-11-17 as API messages; the frontend shows its own approved copy per code), request ID in every log line. **Version pinned:** Spring Cloud **2025.1.4** (Spring Cloud Gateway 5.0.4, Web MVC starter) in `backend/pom.xml`; start.spring.io lists it for Spring Boot ≥4.0.0 <4.2.0-M1. Changing it needs owner approval.
- 2026-11-17 — API foundation review decisions (owner accepted all): services skip CSRF and HTTP sessions (the gateway checks CSRF and strips cookies and identity-like headers; services publish no host port); one log line per request (method, path, status, ms; no query, body, headers, cookies; health checks skipped); per-service access-matrix rows and the real gateway → service request-ID check are done in Public session page; unknown routes answer 401, not 404. API foundation is `DONE`. Header prefix `X-Internal-` is reserved for headers only the gateway sets.
- 2026-11-17 — The owner deleted `nana/tesst.txt`; do not restore it. The owner asked Claude to commit and push planning work to `main` this time (commit and push rules: see memory, no Claude co-author line).

## Unrelated existing files

`README.md` is the project README. It is an empty file by owner request since 2026-11-17 (see "Owner decisions"); do not fill it unasked. (`nana/tesst.txt` was deleted by the owner on 2026-11-17.)

## Two development machines in parallel

The owner works on both a **MacBook Pro (Apple Silicon, arm64, Docker Desktop)** and a **Windows PC using WSL**. Everything Docker-related must run unchanged on both. Follow [.claude/rules/docker-crossplatform.md](.claude/rules/docker-crossplatform.md). Code moves between machines only through git.

## Target repository layout (microservices, AD-13; replaces the blueprint's single `backend/` app)

```text
compose.yaml  .env.example  .gitignore  README.md
backend/
  pom.xml          parent + aggregator (one `mvn verify` for everything)
  Dockerfile       one file, build arg MODULE picks the service
  libs/            common-web (errors, request ID, test support), matching (pure matcher)
  services/        gateway, auth-service, player-service, playmatch-service, rating-service (S05)
frontend/          React + TypeScript + Vite + Tailwind; /api proxied to the gateway
```

Until F18 is implemented, the code is still the single app in `backend/src/`.

## Project guidance files

- Rules: [.claude/rules/](.claude/rules/) — `privacy-auth.md`, `backend-db.md`, `frontend-matching.md`, `docker-crossplatform.md`, `conventions.md` (naming, structure, patterns for every task), `planning-workflow.md` (feature status and evidence)
- Skills: `/feature` (create a feature / run its next phase / check the board), `/spec-lookup`, `/acceptance-check`, `/run-local`, `/new-endpoint`
- Planning: [planning/README.md](planning/README.md) (board), [planning/ROADMAP.md](planning/ROADMAP.md), [planning/architecture/system-design.md](planning/architecture/system-design.md)
- Agents: `scope-guardian` (spec/scope review), `privacy-reviewer` (data leak + authorization review), `spring-react-builder` (implementation following §17)
