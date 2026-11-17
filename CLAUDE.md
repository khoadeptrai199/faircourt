# FairCourt — instructions for Claude

FairCourt is a responsive web app that helps office badminton groups form fair doubles matches during one play session. This repository builds the **MVP (module 1: Fair Matcher) only**.

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
- Out of scope (§0, §2, §3 "Explicitly later"): venue booking, live tournament, swing video, AI rankings, Elo, peer voting, payment, chat, public leaderboards, injury advice, public signup, Kafka, Redis, WebSocket, microservices.

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
- 2026-11-12 — Optional backend dev mode `compose.backend-dev.yaml` (enabled per machine with `COMPOSE_FILE=compose.yaml:compose.backend-dev.yaml` in `.env`): `mvn spring-boot:run` on mounted source + `spring-boot-devtools` (runtime, optional; not in the packaged jar) → app restarts after a compile. Default stack unchanged.
- 2026-11-12 — Vite polling is opt-in via `VITE_USE_POLLING=true` in `.env` (needed on `/mnt/d`; observed: no file-change events without it). Default `false`, so the Mac is unchanged.
- 2026-11-12 — **Host ports moved to 9090+** (owner request, to avoid the HRM project which uses 8080, 8081, 1433, 6379, ...): backend `9090`, frontend `9091`, JDWP debug `9092`; overridable via `BACKEND_PORT` / `FRONTEND_PORT` / `BACKEND_DEBUG_PORT` in `.env`. Only the host side of the mapping changed; container-internal ports stay 8080 / 5173 / 5005. This intentionally deviates from blueprint §0 (`localhost:5173`, `:8080`). Always use 9090+ for any new published port; never HRM ports. App URL is now http://localhost:9091.
- 2026-11-13 — PostgreSQL published to the host for DBeaver: `127.0.0.1:${POSTGRES_PORT:-9093}` -> container 5432 (loopback only, 9090+ range). Owner asked to connect DBeaver; DB admin client only, Flyway still owns the schema. Note for agents: WSL on the Windows machine idles to `Stopped` and takes Docker with it; keep a WSL window open (see `docs/guides/WSL_LOCAL_DEV.md` section 4).
- 2026-11-12 — Home page at `/` approved: hero + "How it works" + "Got a session link?" panel + disabled "Host login · Coming soon" button; English copy. Exact copy awaits owner review.
- 2026-11-17 — **Planning tracker `planning/` (committed to git)** is the single place to create features and track status, design, plan and evidence (one folder per feature under its sprint). It replaces the local-only Codex work-item flow (`docs/work-items/`, `$feature-flow`); those Codex files are left untouched. Planning docs are written in English. Added rules `conventions.md` and `planning-workflow.md` and the `/feature` skill. The sprint plan, `AD-xx` architecture decisions and `OD-xx` open decisions in `planning/` are **proposals** until the owner approves each one.

## Unrelated existing files

`nana/tesst.txt` pre-dates this setup. Do not modify or delete it without asking. `README.md` is the project README; the owner allowed keeping its run instructions and verification log up to date (2026-11-12).

## Two development machines in parallel

The owner works on both a **MacBook Pro (Apple Silicon, arm64, Docker Desktop)** and a **Windows PC using WSL**. Everything Docker-related must run unchanged on both. Follow [.claude/rules/docker-crossplatform.md](.claude/rules/docker-crossplatform.md). Code moves between machines only through git.

## Target repository layout (blueprint §0, §15, §16)

```text
compose.yaml  .env.example  .gitignore  README.md
backend/   Spring Boot app, Flyway migrations, seed, tests
frontend/  React + TypeScript + Vite + Tailwind
```

## Project guidance files

- Rules: [.claude/rules/](.claude/rules/) — `privacy-auth.md`, `backend-db.md`, `frontend-matching.md`, `docker-crossplatform.md`, `conventions.md` (naming, structure, patterns for every task), `planning-workflow.md` (feature status and evidence)
- Skills: `/feature` (create a feature / run its next phase / check the board), `/spec-lookup`, `/acceptance-check`, `/run-local`, `/new-endpoint`
- Planning: [planning/README.md](planning/README.md) (board), [planning/ROADMAP.md](planning/ROADMAP.md), [planning/architecture/system-design.md](planning/architecture/system-design.md)
- Agents: `scope-guardian` (spec/scope review), `privacy-reviewer` (data leak + authorization review), `spring-react-builder` (implementation following §17)
