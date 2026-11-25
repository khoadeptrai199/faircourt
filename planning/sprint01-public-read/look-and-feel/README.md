# F20 — Look and feel ("Court lines")

| Field | Value |
|---|---|
| Sprint | S01 Services + public read |
| Status | `DONE` |
| Blueprint | §6 (colors, typography, tone), §16 (layout, components, page states); applies to the home page and the public game page |
| Depends on | F02 (Public game page) |
| Waiting for | — |
| Suggested branch | `feature/look-and-feel` |
| Last updated | 2026-11-25 |

Docs: [design.md](design.md) · [implement.md](implement.md)

## Goal

FairCourt has its own recognisable look ("chất riêng"): young, lively, clear, not flashy, so a first-time visitor is impressed at first sight. Applied to the two pages that exist (home, public game page) and reused by every later page.

## Scope

- In: visual style of the home page and the public game page (open, closed, not found, error) at phone and desktop width; shared tokens and base components.
- Out: new pages or behavior; dark mode; animations beyond simple hover/focus.

## Acceptance criteria (draft until APPROVED)

| ID | Observable criterion | Verification |
|---|---|---|
| AC-01 | Home and public game page match the approved mockup at 390px and 1280px | visual (owner) |
| AC-02 | Works from 320px without horizontal scroll; Join bar stays reachable on a phone | visual |
| AC-03 | Text contrast ≥ 4.5:1 (3:1 for 24px+); status shown as text + color; focus visible; targets ≥ 44px | checked values in design.md + visual |
| AC-04 | No new user-facing string without approval; pipeline green | copy table + pipeline |

## Decision log

| Date | Decision | Source |
|---|---|---|
| 2026-11-18 | Owner: "UI bạn tự vẽ. Năng động trẻ trung trực quan và thu hút là được, không cần quá cầu kì màu mè … ấn tượng ngay lần đầu tiên", with its own identity ("chất riêng"). Feature created in S01 at the owner's request; mockup made, waiting for approval | Owner, conversation 2026-11-18 |
| 2026-11-18 | Owner approved the look as shown in the mockup "for now" ("ok tạm thời UI như ảnh đi, sau này sẽ sửa UI lại"): covers D-2 (new color tokens), D-3 ("6+ / sets per player" tile), D-4 (Join bar fixed at the bottom on phones). D-1 (font loading) still open. Owner also described navigation (5 items desktop top bar, 4–5 phone bottom bar) and the discovery → detail → join flow: mocked up as LATER artboards (home with nav + games near you), ideas recorded as L-20…L-25 in ROADMAP | Owner, conversation 2026-11-18 |
| 2026-11-18 | D-1 = (a): Inter bundled through `@fontsource/inter` 5.3.0 (exact, checked on npm). Implemented; status `IN_REVIEW`. Owner asked to commit and push when done | Owner, conversation 2026-11-18 |
| 2026-11-25 | Owner looked at the pages and approved ("I approved. mark it done"): AC-01, AC-02 accepted by the owner. Status `DONE` | Owner, conversation 2026-11-25 |
