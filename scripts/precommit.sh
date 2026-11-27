#!/usr/bin/env bash
# FairCourt pre-commit pipeline (owner rule 2026-11-16). Exits non-zero on the first failure.
# Same steps as the verified pipeline of 2026-11-16, plus a guard against editing applied migrations.
# Run from anywhere inside the repository, on macOS or inside WSL (Docker must be reachable).
# Optional: FLOW_BASE=<branch> sets the diff base for the migration guard (default: main).
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
base="${FLOW_BASE:-main}"

step() { printf '\n== %s\n' "$1"; }

step "1/6 backend format (spotless:apply)"
docker compose run --rm --build backend-format

step "2/6 frontend format (prettier --write)"
docker compose run --rm --no-deps frontend npm run format

step "3/6 backend clean verify (unit + integration tests + spotless:check)"
docker compose --profile test run --rm --build backend-test mvn -B clean verify

step "4/6 frontend format:check + unit tests + typecheck + build"
docker compose run --rm --no-deps frontend sh -c "npm run format:check && npm test && npm run build"

step "5/6 no forbidden files staged (.env, .idea/)"
if git diff --cached --name-only | grep -E '(^|/)\.env$|(^|/)\.idea/'; then
  echo "FAIL: unstage the files above (git restore --staged <file>)"
  exit 1
fi

step "6/6 no applied migration edited, renamed or deleted"
# Committed migrations changed in the working tree, and migrations that exist on the base but changed since.
changed="$(
  {
    git diff --name-status HEAD -- '*/db/migration/*.sql'
    if git rev-parse --verify --quiet "$base" >/dev/null; then
      git diff --name-status "$base"...HEAD -- '*/db/migration/*.sql'
    else
      echo "WARN: base '$base' not found; only uncommitted changes checked" >&2
    fi
  } | grep -E '^(M|D|R)' || true
)"
if [ -n "$changed" ]; then
  echo "$changed"
  echo "FAIL: an existing migration was changed. Add a new V<n>__ file instead (backend-db rule)."
  exit 1
fi

git status --short
echo
echo "PIPELINE OK"
