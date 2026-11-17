# Planning workflow: features, status and evidence

`planning/` (committed) is the single place where features are created, designed, tracked and reviewed (owner decision 2026-11-17). It replaces the local-only Codex work-item flow (`docs/work-items/`, `$feature-flow`); do not create work items there. Board and folder layout: `planning/README.md`.

## Before any task

1. Find the feature the task belongs to on `planning/README.md`. If none fits, propose a new feature (`/feature new …`) and wait — do not start untracked work.
2. Read the feature `README.md` and, if present, `design.md` and `implement.md`.
3. Check the status:
   - `BACKLOG` / `DESIGN` / `WAITING_APPROVAL` → only design work. No application code.
   - `APPROVED` / `IN_PROGRESS` / `CHANGES_REQUESTED` → implement tasks from `implement.md`.
   - `IN_REVIEW` → review only (run `scope-guardian` and `privacy-reviewer`, fill `review.md`).
   - `DONE` → report; change nothing. New work on a done feature is a new feature.
   - `BLOCKED` → name the blocker and ask.
4. Read the relevant blueprint sections (`/spec-lookup`) and `.claude/rules/conventions.md`.

## Phases and who may move a status

| From → to | Allowed when | By |
|---|---|---|
| `BACKLOG → DESIGN` | Owner asks for the design, or `/feature FNN` | Claude |
| `DESIGN → WAITING_APPROVAL` | `design.md` complete from the template: API, data, rules, components, copy table, test plan, open decisions | Claude |
| `WAITING_APPROVAL → APPROVED` | The owner explicitly approves in the conversation; record date + scope of the approval in the feature decision log | **Owner only** |
| `APPROVED → IN_PROGRESS` | Definition of Ready on `planning/README.md` is met | Claude |
| `IN_PROGRESS → IN_REVIEW` | All tasks `DONE`/`SKIPPED (reason)`; every AC has evidence in `implement.md`; pre-commit pipeline run | Claude |
| `IN_REVIEW → CHANGES_REQUESTED` | Any open high/medium finding | Claude |
| `IN_REVIEW → DONE` | Definition of Done met and the owner confirms (owner commits) | Owner confirms |
| any → `BLOCKED` | A named decision or prerequisite is missing | anyone |

Never skip `APPROVED`. An explicit owner instruction such as "implement F04 as designed" counts as approval; record it.

## Keeping status in one truth

Whenever a status changes, update all three in the same step:

1. the feature `README.md` header (`Status`, `Waiting for`, `Last updated`) and its decision log;
2. the feature row on `planning/README.md` (and `Last updated` there);
3. the feature row in its sprint's `SPRINT.md`.

`/feature board` checks that the three agree.

## Evidence rules (owner hard rules 1 and 4)

- `implement.md` → "Commands executed" lists only commands that were actually run, with the real result and the machine.
- An AC is `PASS` only with evidence from this feature's work; otherwise `FAIL` or `NOT VERIFIED` with the reason.
- Copying an older result is allowed only with its date and source (e.g. "README verification log 2026-11-12").

## Decisions

- Product questions the blueprint does not answer → `OD-xx` in `planning/ROADMAP.md` §5 with options and a recommendation; also list it in the feature's "Open decisions". Do not decide it silently.
- Technical design choices → `AD-xx` in `planning/architecture/decisions.md`, or a `D-n` inside one feature's design if it only affects that feature.
- When the owner decides: update the OD/AD row (status, date), the feature decision log, and `CLAUDE.md` → "Owner decisions" if it affects the whole project.

## Scope control

- Ideas from research or conversation that are not in the blueprint MVP go to `planning/ROADMAP.md` §6 "Later backlog" (`L-xx`), never straight into a sprint.
- Adding a feature to a sprint, moving it between sprints, or splitting it needs the owner's OK; record it in `SPRINT.md` notes.

## Sprint files

- `SPRINT.md` status: `PLANNED → ACTIVE → DONE`. A sprint is `DONE` when its gate evidence table is filled with `PASS` (or owner-accepted `NOT VERIFIED`) and all its features are `DONE`.
- Keep the demo steps runnable; update them if the implementation changed the flow.
