# F19 review — Game rename

Date: 2026-11-18. Reviewed together with F02 (same branch); full findings in [F02 review.md](../public-game-page/review.md).

- `scope-guardian`: no V1 migration or `backend/legacy/` file touched; V2 renames in place in each service's own schema, no data dropped; no cross-schema reference. Folder renames `create-session` → `create-game`, `my-game-assignment` → `my-match-assignment`; `public-session-page` files were rewritten while moved, so git shows them as delete + add (content kept under `public-game-page/`).
- `privacy-reviewer`: V2 uses `format('%I')` limited to `current_schema()`; no privacy impact.
- No open high/medium finding for this feature.
