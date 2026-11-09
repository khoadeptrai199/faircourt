# FairCourt — instructions for Claude

FairCourt is a responsive web app that helps office badminton groups form fair doubles matches during one play session. This repository builds the **MVP (module 1: Fair Matcher) only**.

## Source of truth

- The only product/technical spec is [FairCourt_Project_Blueprint.md](FairCourt_Project_Blueprint.md).
- When its wording differs, precedence is (blueprint §0): **§0 local delivery contract → Part II (§11–17) → Part I (§1–10)**.
- Before implementing anything, look up the relevant section (use `/spec-lookup`). Cite section numbers in plans, commits and reviews.
- If the blueprint is silent, ambiguous or contradicts itself on a point that affects behavior → **stop and ask the owner**. Do not pick an answer silently.

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
- Exact JDK / Spring Boot / Node / React / Vite / PostgreSQL versions are not chosen yet (§0 requires pinned stable versions). Propose them with evidence, get approval, then pin.
- Architecture of the second (WSL) machine is unknown — check with `uname -m` there.

## Owner decisions (resolved)

- 2026-11-09 — Seed: **8** READY member players, as in §0 (not 12 from §3/§17). Owner context: one court usually holds at most 8 people. This is **not** an app rule — do not add any attendance cap.
- 2026-11-09 — `.gitattributes` enforcing LF approved and added.

## Unrelated existing files

`README.md` (current content) and `nana/tesst.txt` pre-date this setup. Do not modify or delete them without asking.

## Two development machines in parallel

The owner works on both a **MacBook Pro (Apple Silicon, arm64, Docker Desktop)** and a **Windows PC using WSL**. Everything Docker-related must run unchanged on both. Follow [.claude/rules/docker-crossplatform.md](.claude/rules/docker-crossplatform.md). Code moves between machines only through git.

## Target repository layout (blueprint §0, §15, §16)

```text
compose.yaml  .env.example  .gitignore  README.md
backend/   Spring Boot app, Flyway migrations, seed, tests
frontend/  React + TypeScript + Vite + Tailwind
```

## Project guidance files

- Rules: [.claude/rules/](.claude/rules/) — `privacy-auth.md`, `backend-db.md`, `frontend-matching.md`, `docker-crossplatform.md`
- Skills: `/spec-lookup`, `/acceptance-check`, `/run-local`, `/new-endpoint`
- Agents: `scope-guardian` (spec/scope review), `privacy-reviewer` (data leak + authorization review), `spring-react-builder` (implementation following §17)
