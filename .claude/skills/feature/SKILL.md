---
name: feature
description: Create a FairCourt feature folder in planning/, run exactly one next phase for an existing feature (design → stop for approval; approved → implement; implemented → review), or check that the planning board, sprint files and feature READMEs agree. Use when the owner says "/feature", asks to start, continue or check a feature, or gives a new task that needs a feature.
argument-hint: "new <SNN> <slug> — <intent> | <FNN> | board"
---

# /feature

Arguments: **$ARGUMENTS**

Read first: `.claude/rules/planning-workflow.md`, `.claude/rules/conventions.md`, `planning/README.md`.

## `new <SNN> <slug> — <intent>`

1. Check `planning/README.md` and `planning/ROADMAP.md`: if an existing feature or backlog item (`L-xx`) already covers the intent, say so and stop.
2. Check scope with `/spec-lookup`. If the intent is outside the blueprint MVP, propose adding it to the later backlog instead and stop.
3. Next ID = highest `F<nn>` on the board + 1. Folder: `planning/sprint<NN>-<name>/F<nn>-<slug>/`. If the sprint folder does not exist, create it from `planning/_templates/SPRINT.md` only after the owner confirms the new sprint.
4. Copy `planning/_templates/feature/README.md`; fill goal, scope, blueprint sections, draft acceptance criteria, dependencies, open decisions. Status `BACKLOG`.
5. Add the row to `planning/README.md` and to the sprint's `SPRINT.md`.
6. Report the path and the open decisions. Stop.

## `<FNN>` — run one phase

Resolve the folder from the board, read the feature files, then act on the status:

| Status | Do this, then stop |
|---|---|
| `BACKLOG`, `DESIGN` | Write `design.md` from the template (spec quotes, API contract, data/transactions, components with patterns from `system-design.md`, copy table, test plan, open decisions). Create `implement.md` with the task list. Set `WAITING_APPROVAL`. Ask the owner the open questions with recommended options. |
| `WAITING_APPROVAL` | Show what is still undecided. Do not implement. If the owner approves in this conversation, record it and set `APPROVED`. |
| `APPROVED`, `IN_PROGRESS`, `CHANGES_REQUESTED` | Check the Definition of Ready. Implement the next tasks (use `/new-endpoint` for routes), run tests, record real commands and evidence in `implement.md`. When all tasks are done and the pre-commit pipeline was run, set `IN_REVIEW`. |
| `IN_REVIEW` | Run the `scope-guardian` and `privacy-reviewer` agents on the feature diff; write `review.md` from the template. Open high/medium findings → `CHANGES_REQUESTED`; otherwise report ready for the owner to confirm `DONE` and commit. |
| `DONE` | Report the evidence summary. Change nothing. |
| `BLOCKED` | Re-check the named blocker; report what is still missing. |

After every status change update the three places listed in `planning-workflow.md` ("Keeping status in one truth").

## `board`

Compare, for every feature: status in its `README.md`, its row on `planning/README.md`, and its row in `SPRINT.md`. Also check that every `OD-xx` / `AD-xx` referenced exists in `ROADMAP.md` / `decisions.md`. Report mismatches as a table and propose fixes; apply them only if the owner agrees.

## Never

- Move a feature to `APPROVED` or `DONE` without the owner.
- Write application code for a feature that is not `APPROVED`.
- Mark an AC `PASS` without evidence from an executed command or check.
- Delete or renumber feature folders or IDs.
