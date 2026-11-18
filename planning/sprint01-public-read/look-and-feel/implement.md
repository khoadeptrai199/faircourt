# F20 implementation — Look and feel ("Court lines")

Design: [design.md](design.md), look approved by the owner 2026-11-18 ("ok tạm thời UI như ảnh đi, sau này sẽ sửa UI lại"); D-1 = (a) approved the same day ("(a) Đề xuất: đóng gói font trong app qua gói npm @fontsource/inter"); owner asked to commit and push when done. Branch `feature/public-game-page` (same branch as F02 and F19, owner asked to commit and push there).

## Tasks

| Task | Covers | Description | Status |
|---|---|---|---|
| T01 | AC-04 | `@fontsource/inter` 5.3.0 (exact) installed in the frontend container; weights 400–900 imported in `main.tsx` | `DONE` |
| T02 | AC-03 | New color tokens + Inter in `styles/tokens.css` | `DONE` |
| T03 | AC-01 | Shared pieces: `Logo`, `CourtLines`, `CourtMiss`; restyled `Button`, `StatusBadge`, `EmptyState`, `ErrorBanner`, `BackHomeLink` | `DONE` |
| T04 | AC-01, AC-02 | `GameHero` (hero card, time, tiles, price, Join bar fixed at the bottom on phones), `PublicGamePage`, `AppRoutes` | `DONE` |
| T05 | AC-01 | `HomePage` restyled | `DONE` |
| T06 | AC-04 | Pipeline | `DONE` |
| T07 | AC-01, AC-02 | Visual check at 320 / 375 / 1280px against the mockup | `NOT VERIFIED` (owner) |

## Files

| File | Added / changed | Why |
|---|---|---|
| `frontend/package.json`, `frontend/package-lock.json` | changed | `@fontsource/inter` 5.3.0 (exact) |
| `frontend/src/main.tsx` | changed | Import Inter 400–900 |
| `frontend/src/styles/tokens.css` | changed | `muted`, `subtle`, `disabled`, `action-dark`, `error-soft`; `--font-sans` starts with Inter |
| `frontend/src/components/{Logo,CourtLines,CourtMiss}.tsx` | added | Brand mark, court-lines motif, not-found picture |
| `frontend/src/components/{Button,StatusBadge,EmptyState,ErrorBanner,BackHomeLink}.tsx` | changed | New look |
| `frontend/src/features/public-game/{GameHero,PublicGamePage}.tsx`, `frontend/src/routes/AppRoutes.tsx` | changed | New layout, header with logo |
| `frontend/src/features/home/HomePage.tsx` | changed | New look; shuttle/court SVG now shared components |
| `frontend/src/copy/en.ts`, `frontend/src/lib/{time,money}.ts` | changed | Strings split for tiles; `formatGameTime`, `formatVndRange` output |

Deleted files: none (the old inline `ShuttleMark` / `CourtLines` in `HomePage.tsx` moved to components).

## Commands executed

WSL Ubuntu 24.04, Docker Engine 29.5.3, 2026-11-18.

| Command | Result |
|---|---|
| `curl https://registry.npmjs.org/@fontsource/inter` | latest 5.3.0, published 2026-07-19, license OFL-1.1 |
| `docker compose exec -T frontend npm install --save-exact --no-audit --no-fund @fontsource/inter@5.3.0` | added 1 package; the running container got the package at once (no stale `node_modules` problem this time) |
| Full pipeline (guide §10.1) | `PIPELINE OK` in 84 s; 95 backend tests, 0 failures; `format:check` and `tsc` + `vite build` OK (119 modules) |
| `curl http://localhost:9091/g/7RQGQ484` and `/src/main.tsx` | 200; `main.tsx` imports the six Inter CSS files; no Vite error in the log |

## Acceptance evidence

| AC | Check | Result | Evidence |
|---|---|---|---|
| AC-01 | visual vs mockup | `NOT VERIFIED` | Built from the mockup; not looked at in a browser by Claude |
| AC-02 | visual 320px | `NOT VERIFIED` | Layout uses `grid-cols-3`, `min-w` free text, `pb-28` for the fixed bar; not checked in a browser |
| AC-03 | contrast values | `PASS` (computed) / `NOT VERIFIED` (browser) | Values in design.md §2; Join disabled text `muted` on `disabled` |
| AC-04 | copy table + pipeline | `PASS` | Same words as the approved copy (split for tiles, noted in `copy/en.ts`); pipeline green |

## Deviations from the design

- The home page lost the small pill "🏸 Badminton doubles, made fair" and the 🔗 emoji (the mockup did not have them; emoji are not allowed by the design rules). If the owner wants the pill back it is one line.
- The build bundles every Inter subset (latin, latin-ext, vietnamese, cyrillic, greek); the browser downloads only the subsets whose characters appear. Vietnamese copy (backlog L-03) works without another change. A latin-only import would shrink `dist/`.
- Reviewers `scope-guardian` / `privacy-reviewer` were not run for this UI-only change (no API, data or access change).
