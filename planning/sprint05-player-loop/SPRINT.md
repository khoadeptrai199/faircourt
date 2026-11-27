# S05 — Player loop

| Field | Value |
|---|---|
| Status | `ACTIVE` |
| Goal | Each player sees only "Your next match: Court X" and its status, refreshed while the page is visible; after a game they can answer Too easy / About right / Too hard; the host can record a private observation and adjust a band |
| Gate | §17 step 4: "four assigned players each see only their own match/court; a fifth sees no assignment". §17 checklist: "Four players see match number and court number after confirm; public viewers do not" |
| Blueprint | §4, §6, §12, §13, §16 |
| Started / finished | — |

## Features

| ID | Feature | Status |
|---|---|---|
| F12 | [My game: own assignment + polling](my-match-assignment/README.md) | `IN_REVIEW` |
| F13 | [Match-fit feedback](match-feedback/README.md) | `BACKLOG` |
| F14 | [Host skill assessment](host-assessment/README.md) | `BACKLOG` |

## Demo at sprint end

1. Four assigned players (incl. the guest) each see their own match number and court; a fifth sees "no match yet".
2. Host starts the match; within 15 s the guest page shows Playing without a manual refresh.
3. After finish, the guest answers "About right"; a second answer is rejected.
4. Host records an observation for the guest and confirms band 2; the board shows `OBSERVED`/`CONFIRMED`.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro
