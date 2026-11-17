# S06 — Acceptance

| Field | Value |
|---|---|
| Status | `PLANNED` |
| Goal | The §0 end-to-end demonstration passes with evidence, the UI works from 320px to desktop, and CI runs the tests |
| Gate | §0 "Required end-to-end demonstration" steps 1–6 and every line of the §17 "Scope acceptance checklist" `PASS` with evidence |
| Blueprint | §0, §6, §7, §16, §17 |
| Started / finished | — |

## Features

| ID | Feature | Status |
|---|---|---|
| F15 | [End-to-end smoke test + acceptance check](e2e-smoke-test/README.md) | `BACKLOG` |
| F16 | [Responsive + accessibility polish](responsive-a11y-polish/README.md) | `BACKLOG` |
| F17 | [CI pipeline](ci-pipeline/README.md) | `BACKLOG` |

## Demo at sprint end

Run `/acceptance-check all` and walk the §0 six steps in two browsers (host + private window guest), including `docker compose down` / `up -d` without `-v`.

## Gate evidence

| Gate item | Result | Evidence |
|---|---|---|

## Notes and retro
