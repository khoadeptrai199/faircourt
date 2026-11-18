# FairCourt planning board

The one place to create features, track their status, and read each feature's design and implementation plan.

- Spec: [FairCourt_Project_Blueprint.md](../FairCourt_Project_Blueprint.md). Precedence: §0 → Part II (§11–17) → Part I. The blueprint wins over anything in this folder.
- Plan: [ROADMAP.md](ROADMAP.md) (sprints, research, open decisions, later backlog)
- System design: [architecture/system-design.md](architecture/system-design.md) · Decision log: [architecture/decisions.md](architecture/decisions.md)
- Rules for agents: [.claude/rules/planning-workflow.md](../.claude/rules/planning-workflow.md), [.claude/rules/conventions.md](../.claude/rules/conventions.md)

Last updated: 2026-11-18

## Sprints

| Sprint | Goal | Gate | Status |
|---|---|---|---|
| [S00 Foundation](sprint00-foundation/SPRINT.md) | Docker stack, V1 schema, seed, pure matcher | §17 step 1 | DONE |
| [S01 Services + public read](sprint01-public-read/SPRINT.md) | Split into gateway + services (one schema each), shared API foundation, game rename, public game page | All services healthy; anonymous sees only public data through the gateway | ACTIVE |
| [S02 Identity](sprint02-identity/SPRINT.md) | Account login, guest check-in, own attendance | §17 step 2 gate | PLANNED |
| [S03 Host board](sprint03-host-board/SPRINT.md) | Host board, approve/rest/leave, create game | Host sees full board; other account 403 | PLANNED |
| [S04 Match loop](sprint04-match-loop/SPRINT.md) | Suggest, swap, confirm, start, finish, cancel | §17 step 3 gate | PLANNED |
| [S05 Player loop](sprint05-player-loop/SPRINT.md) | My game, polling, feedback, host assessment | §17 step 4 gate | PLANNED |
| [S06 Acceptance](sprint06-acceptance/SPRINT.md) | E2E smoke, responsive/a11y polish, CI | §0 demo + §17 checklist | PLANNED |

## Features

| ID | Feature | Sprint | Status | Depends on | Waiting for | Docs |
|---|---|---|---|---|---|---|
| F00 | Walking skeleton | S00 | DONE | — | — | [README](sprint00-foundation/walking-skeleton/README.md) |
| F18 | Service split skeleton (gateway + auth/player/playmatch services, schema per service) | S01 | DONE | F00 | — | [README](sprint01-public-read/service-split/README.md) · [design](sprint01-public-read/service-split/design.md) · [implement](sprint01-public-read/service-split/implement.md) |
| F01 | API foundation: shared error lib, gateway route table, request ID, CSRF bootstrap, test harness | S01 | DONE | F18 | — | [README](sprint01-public-read/api-foundation/README.md) · [design](sprint01-public-read/api-foundation/design.md) · [implement](sprint01-public-read/api-foundation/implement.md) |
| F19 | Game rename ("session" → "game": tables, routes, names, copy) | S01 | DONE | F18, F01 | — | [README](sprint01-public-read/game-rename/README.md) · [design](sprint01-public-read/game-rename/design.md) · [implement](sprint01-public-read/game-rename/implement.md) |
| F02 | Public game page (+ format, minimum sets, price) + frontend foundation | S01 | DONE | F01, F19 | — | [README](sprint01-public-read/public-game-page/README.md) · [design](sprint01-public-read/public-game-page/design.md) · [implement](sprint01-public-read/public-game-page/implement.md) |
| F20 | Look and feel ("Court lines"): own visual identity for home + public game page | S01 | IN_REVIEW | F02 | owner looks at the pages (320/375/1280px) | [README](sprint01-public-read/look-and-feel/README.md) · [design](sprint01-public-read/look-and-feel/design.md) |
| F03 | Account login, logout, `GET /me` | S02 | BACKLOG | F01 | OD-03 | [README](sprint02-identity/account-login/README.md) |
| F04 | Guest check-in + guest cookie | S02 | BACKLOG | F02 | OD-02, OD-06 | [README](sprint02-identity/guest-check-in/README.md) |
| F05 | Own attendance status + member join | S02 | BACKLOG | F03, F04 | OD-02, OD-11 | [README](sprint02-identity/my-attendance-status/README.md) |
| F06 | Host board | S03 | BACKLOG | F03 | OD-04, OD-14 | [README](sprint03-host-board/host-board/README.md) |
| F07 | Attendance management (approve, rest, leave) | S03 | BACKLOG | F06 | OD-07, OD-15 | [README](sprint03-host-board/attendance-management/README.md) |
| F08 | Create game | S03 | BACKLOG | F03 | OD-05, OD-10 | [README](sprint03-host-board/create-game/README.md) |
| F09 | Match suggestions (read-only) | S04 | BACKLOG | F07 | — | [README](sprint04-match-loop/match-suggestions/README.md) |
| F10 | Match confirm + tap-to-swap review | S04 | BACKLOG | F09 | OD-08 (optional) | [README](sprint04-match-loop/match-confirm/README.md) |
| F11 | Match start / finish / cancel | S04 | BACKLOG | F10 | OD-07 | [README](sprint04-match-loop/match-lifecycle/README.md) |
| F12 | My game: own assignment + polling | S05 | BACKLOG | F05, F11 | OD-16 | [README](sprint05-player-loop/my-match-assignment/README.md) |
| F13 | Match-fit feedback | S05 | BACKLOG | F12 | OD-16 | [README](sprint05-player-loop/match-feedback/README.md) |
| F14 | Host skill assessment | S05 | BACKLOG | F06 | — | [README](sprint05-player-loop/host-assessment/README.md) |
| F15 | End-to-end smoke test + acceptance check | S06 | BACKLOG | F13 | OD-08 | [README](sprint06-acceptance/e2e-smoke-test/README.md) |
| F16 | Responsive + accessibility polish | S06 | BACKLOG | F12 | — | [README](sprint06-acceptance/responsive-a11y-polish/README.md) |
| F17 | CI pipeline | S06 | BACKLOG | F01 | OD-09 | [README](sprint06-acceptance/ci-pipeline/README.md) |

Open decisions (OD-xx) are listed with options and a recommendation in [ROADMAP.md §5](ROADMAP.md#5-open-decisions).

## How this folder works

### Layout

```text
planning/
  README.md                 this board (update it whenever a status changes)
  ROADMAP.md                sprint plan, research, open decisions, later backlog
  architecture/
    system-design.md        system design and pattern catalog
    decisions.md            architecture decision log (AD-xx)
  _templates/               copy these when creating a sprint or feature
  sprintNN-<name>/
    SPRINT.md               sprint goal, gate, feature list, demo, retro
    <feature-name>/         one folder per feature (plain name, no ID)
      README.md             status card: goal, scope, acceptance criteria, decisions
      design.md             technical design (written in phase DESIGN)
      implement.md          tasks, files, commands run, evidence per AC (phase IN_PROGRESS)
      review.md             reviewer findings and gate result (phase IN_REVIEW)
```

A feature folder starts with `README.md` only; `design.md`, `implement.md` and `review.md` are added when that phase starts. Feature IDs (`F01`, `F02`, …) are unique across all sprints and never reused. Acceptance criteria are numbered per feature (`AC-01`) and referenced from elsewhere as `F04/AC-02`.

### Status values

| Status | Meaning | Who moves it |
|---|---|---|
| `BACKLOG` | Folder + README with goal, scope, draft acceptance criteria | Claude or owner |
| `DESIGN` | `design.md` is being written | Claude |
| `WAITING_APPROVAL` | Design complete; open decisions listed for the owner | Claude |
| `APPROVED` | Owner approved the design (date recorded in the decision log) | **Owner only** |
| `IN_PROGRESS` | Tasks in `implement.md` are being done | Claude |
| `IN_REVIEW` | All tasks done with evidence; `scope-guardian` + `privacy-reviewer` run; `review.md` filled | Claude |
| `CHANGES_REQUESTED` | Review found issues; goes back to `IN_PROGRESS` | Claude |
| `DONE` | Review passed, pre-commit pipeline green, docs synced, owner committed | Owner confirms |
| `BLOCKED` | Waiting for a named decision or prerequisite | Anyone |

### Definition of Ready (before `IN_PROGRESS`)

- Design `APPROVED` by the owner; every blocking OD for this feature is decided.
- Dependencies are `DONE`, or the owner explicitly allowed starting earlier.
- All new user-facing copy in the design's copy table is approved.
- No new dependency, config file or route outside the blueprint without an approval entry in the decision log.

### Definition of Done

- Every acceptance criterion has `PASS` evidence from an actually executed command or check, or an owner-accepted `NOT VERIFIED` note.
- Tests added per [conventions](../.claude/rules/conventions.md): unit tests for rules, PostgreSQL integration tests, `AccessMatrixIT` rows for every new route, JSON allowlist assertions for every non-host response.
- Pre-commit pipeline green (`docs/guides/WSL_GITHUB_WORKFLOW.md` §10 on the Windows machine; same steps on the Mac).
- `scope-guardian` and `privacy-reviewer` report no open high/medium findings.
- `API Document/*.md` and the feature's `implement.md` reflect the delivered behavior (`README.md` stays empty by owner request 2026-11-17); this board, the sprint file and the feature README show the new status.

### Creating or advancing a feature

Use the `/feature` skill:

- `/feature new S03 waitlist-chip — short intent` creates the `waitlist-chip/` folder (plain name, next free ID inside the files) from the templates, status `BACKLOG`, and adds it to this board and the sprint file.
- `/feature F04` runs exactly one next phase for F04 (design → stop for approval; approved → implement; implemented → review) and updates all status places.
- `/feature board` checks that this board, the sprint files and the feature READMEs agree.
