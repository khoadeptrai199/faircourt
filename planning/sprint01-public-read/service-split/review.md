# F18 review — Service split skeleton

## Reviewers

| Reviewer | Date | Scope reviewed | Verdict |
|---|---|---|---|
| `scope-guardian` (fresh agent) | 2026-11-17 | commit `ec46085` (`git diff 15c4edf ec46085`) | CHANGES NEEDED (small fixes + owner questions) |
| `privacy-reviewer` (fresh agent) | 2026-11-17 | same commit | ISSUES FOUND (none high) |
| Owner | — | — | pending: decisions D-R1, D-R2 below |

Neither reviewer ran tests. Both confirmed: schema matches design §4 and blueprint §12 (all constraints and indexes kept, only cross-schema FKs removed); seed matches §0 and the 8-player decision; only ports 9090–9093 on `127.0.0.1`; players' gender is not stored; passwords hashed; seed local-only.

Fixes were made on branch `feature/api-foundation` (it already contains the Service split commit) and re-tested; see [implement.md](implement.md) "Review fixes".

## Findings

| # | Severity | Where | Finding | Action | Status |
|---|---|---|---|---|---|
| P1 | medium | `*/SchemaBoundaryTest.java` | The boundary regex could be bypassed (quoted names, `@Table(schema=…)`, `default_schema`, `currentSchema`, concatenated SQL, tables not in a fixed list) | One shared `SchemaBoundary` check (common-web test support): any qualified name of another schema in SQL text (migrations, resources, Java string literals and text blocks), any setting/statement pointing at another schema, legacy `public` included; plus a runtime check in every IT that `search_path` holds only the service schema | FIXED |
| S4 | low | same | Narrower than design §6 ("scan for `auth.`, `player.` …") | Covered by P1. Remaining difference: plain Java code (outside string literals) is not scanned for qualified names, to avoid false positives such as `auth -> auth.requestMatchers(...)` | FIXED (difference recorded) |
| P4 | low | all four apps | Spring Boot created a default user and logged "Using generated security password" (confirmed in the live logs) | common-web registers a no-users `UserDetailsService` before Boot's auto-configuration | FIXED |
| S1 | low | `implement.md` | Deleted-files list incomplete | Corrected | FIXED |
| S2 | low | README emptied | README's implementation decisions, version table and verification log were lost; stale pointers in `ReasonCode.java`, `compose.yaml`, `backend/pom.xml`, `CLAUDE.md` | Copied unchanged to `planning/sprint00-foundation/walking-skeleton/README.md` ("Records moved from README.md"); pointers fixed | FIXED |
| S3 | low | `backend/.dockerignore` | `target` matched only the root folder; module `target/` folders entered the image build context | `**/target` | FIXED |
| S5 | low | design §4 | Flyway/Hikari property names checked by running, not against the reference | Runtime guard now proves the effect in every IT (`current_schema()` and `search_path`) | FIXED (by test) |
| P3 | low | existing local volumes | Old single-app tables remain in `public` | Kept on purpose (owner decision D-1, 2026-11-17); the boundary check now also flags any `public.` reference | ACCEPTED (owner, D-1) |
| P5 | info | Compose network | Services are reachable from other containers on the internal network | By design (AD-14); services deny everything except health; identity-token checks come with Account login | NOTE |
| S7 | info | `design.md` | Approval quote "làm hết" not verifiable by the reviewer | The owner gave it in the conversation of 2026-11-17 (answer to "start F18?") | NOTE |
| P2 | low | `.env.example` | JDWP debugging is on by default (`BACKEND_DEBUG_OPTS=…address=*:5005`); the host port is loopback-only, but other containers can reach port 5005 of playmatch-service | Owner decision D-R1 | OPEN |
| S6 | low | AC-01, sprint gate | Not run on Windows WSL; that machine's `.env` still has `COMPOSE_FILE=…compose.backend-dev.yaml`, so Compose fails there until the line is deleted | Owner decision D-R2 | OPEN |

## Owner decisions needed

| ID | Question | Recommendation |
|---|---|---|
| D-R1 | Make JDWP debugging opt-in: `BACKEND_DEBUG_OPTS=` empty in `.env.example` (each machine turns it on in its own `.env` when debugging) | Yes |
| D-R2 | Accept "not verified on WSL" for now, or run it on WSL first (delete the `COMPOSE_FILE` line in that `.env`, then `docker compose up --build -d`) | Run on WSL when you next use that machine; accept for now |

## Acceptance trace

| AC | Verdict |
|---|---|
| AC-01 | PASS (Mac) · NOT VERIFIED (WSL) |
| AC-02 … AC-06, AC-08 … AC-10 | PASS (Mac; see implement.md) |
| AC-05 | PASS, strengthened by the review fix |
| AC-07 | PASS (Mac) · NOT VERIFIED (WSL) |

## Gate

- Pre-commit pipeline after the fixes: see [implement.md](implement.md) "Review fixes".
- Result: no open high or medium finding; **waiting for owner decisions D-R1, D-R2 and confirmation** before `DONE`.
- Not verified: Windows WSL.
