# FairCourt tracker adapter for `/flow`

How the generic three-gate loop maps onto `planning/` and `.claude/rules/planning-workflow.md`. Where the two differ, `planning-workflow.md` wins.

## Resolving a feature

`/flow <feature>` takes the feature's plain name (`guest-check-in`) or its ID (`F04`). Look it up on `planning/README.md`; the folder is `planning/sprint<NN>-<name>/<feature-name>/`. No row → stop and propose `/feature new …`.

## Files

| Engine artefact | FairCourt file |
|---|---|
| Spec (Gate 1) | `design.md` (template `planning/_templates/feature/design.md`) |
| Plan + test cases (Gate 2) | `implement.md` → "Tasks", "Files" and "Test cases" sections |
| Evidence | `implement.md` → "Commands executed", "Acceptance evidence" |
| Report (Gate 3) | `review.md` (template `planning/_templates/feature/review.md`) + the summary in the conversation |
| Gate records | the feature `README.md` decision log, one line per gate: `<date> — Gate <n> (<spec|plan>) approved by the owner: <scope>` |

## Statuses per phase

| Engine step | Status before → after |
|---|---|
| design | `BACKLOG`/`DESIGN` → `WAITING_APPROVAL` |
| Gate 1 approved | `WAITING_APPROVAL` → `APPROVED` (decision-log line "Gate 1 (spec)") |
| plan | stays `APPROVED`; `README.md` "Waiting for" = "Gate 2: owner approves plan and test cases" |
| Gate 2 approved | `APPROVED` → `IN_PROGRESS` (decision-log line "Gate 2 (plan)") |
| build loop running | `IN_PROGRESS`; fix rounds after review → `CHANGES_REQUESTED` → `IN_PROGRESS` |
| Gate 3 report | `IN_REVIEW`; "Waiting for" = "Gate 3: owner reviews the result and commits" |
| owner confirms | `DONE` (owner only) |

Every status change updates the three places in `planning-workflow.md` ("Keeping status in one truth").

Diff base: feature branches are stacked (each cut from the previous feature branch, not from `main`). The base for review is the branch this feature branch was cut from: find it with `git merge-base` against the other `feature/*` branches and `main`, and use the newest one that is an ancestor.

A free-form decision-log line in which the owner approves the design counts as Gate 1 for features designed before 2026-11-18. Older features whose design was approved before 2026-11-18 have no Gate 2 line: run `/flow <feature> plan` to write and approve the plan before building.

## Runner on this project

- Windows machine: validators run inside WSL. Run them from the repository root with `wsl.exe -e bash -lc '<command>'` (single quotes outside, because the frontend command has double quotes inside); WSL starts in the same folder (`/mnt/d/External/Personal/faircourt`, checked 2026-11-18). Docker lives only in WSL there. If WSL was idle and stopped, the first call starts it; if `docker` is not reachable, report "not verified".
- Mac: run the command directly in the repository root.
- `validators.fast` backend: replace `<module>` with the touched modules, for example `libs/matching` or `services/auth-service` (comma-separated). Frontend fast check is the second command.

## Reviewers

`flow-reviewer` (rubric + override gates in `config.json`), `scope-guardian` (blueprint scope), `privacy-reviewer` (data leak + authorization). Run all three in parallel.
