# F16 — Responsive + accessibility polish

| Field | Value |
|---|---|
| Sprint | S06 Acceptance |
| Status | `BACKLOG` |
| Blueprint | §6 (touch targets, contrast, status text + color, navigation), §16 (breakpoints, page-state matrix, keyboard), §17 checklist ("Phone layout works at 320px+; loading, empty, permission, expired access and conflict states are readable") |
| Depends on | F12 |
| Waiting for | — |
| Suggested branch | `feature/F16-responsive-a11y-polish` |
| Last updated | 2026-11-17 |

## Goal

Every screen works from 320px to desktop, every state is readable, colors pass contrast checks, and everything is keyboard-operable.

## Scope

- In: compact bottom navigation under 768px (`Tonight`, `My game`, host `Manage`, §6); measured contrast ratios for each token pair used for text (recorded, not assumed); focus styles; review of loading/empty/permission/expired/conflict states on every page; replace raw hex values in the home page SVGs with tokens where possible; carry over F00/AC-05 (home page visual check).
- Out: animations beyond what §6 allows ("quick and optional"); PWA (L-02).

## Acceptance criteria (draft)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | All routes usable at 320, 375, 768 and 1280px without horizontal scroll | visual (screenshots or `NOT VERIFIED`) |
| AC-02 | Text/background token pairs meet WCAG 2.2 AA for their size; failures fixed or documented | contrast calculation recorded |
| AC-03 | All actions reachable and visible-focused by keyboard, incl. tap-to-swap | manual |
| AC-04 | Every page-state from §16 is reachable and readable | manual checklist |
