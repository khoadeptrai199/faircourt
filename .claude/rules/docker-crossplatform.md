---
paths:
  - "compose.yaml"
  - "**/Dockerfile"
  - "**/.dockerignore"
  - ".env.example"
  - ".gitattributes"
  - "**/*.sh"
  - "**/mvnw"
  - "frontend/vite.config.*"
---

# Docker on two machines: macOS (Apple Silicon) + Windows WSL

The owner develops in parallel on:

| Machine | Known facts | Still to verify |
|---|---|---|
| MacBook Pro | macOS 15.1, `arm64`, Docker 28.0.4, Compose v2.34 (Docker Desktop), repo under `~/Desktop` | Docker Desktop must have macOS permission for the Desktop folder, otherwise bind mounts fail with `mkdir /host_mnt/...: operation not permitted` (seen 2026-11-09) |
| Windows PC | WSL Ubuntu 24.04, `x86_64`, Docker Engine 29.5.3 inside WSL (no Docker Desktop), Compose v5.1.4; repo on `/mnt/d` by owner choice → `VITE_USE_POLLING=true` | — |

Goal: the exact §0 commands work **unchanged** on both:

```bash
cp .env.example .env        # once per machine; never overwrite an existing .env
docker compose up --build -d
docker compose down         # keeps the DB volume
```

## Images and builds

- Use only official images that publish **both `linux/amd64` and `linux/arm64`**. Before pinning a tag, verify with `docker buildx imagetools inspect <image>:<tag>` and record the result. No `latest` tags (§0).
- Do **not** set `platform:` in `compose.yaml` and do not hardcode an arch in Dockerfiles. No downloading arch-specific binaries by URL.
- Build inside containers with multi-stage Dockerfiles (Maven/JDK build stage → JRE runtime; `npm ci` inside the image). The host's installed Java/Node versions must not matter.
- `.dockerignore` must exclude at least `node_modules`, `dist`, `target`, `.env`, `.git` so host-built artifacts never enter the image.
- Health checks must use a command that exists in the image (`pg_isready` exists in the official postgres image; for the backend image check whether `curl`/`wget` exists before using it — do not assume).

## Bind mounts and dev servers

- If frontend source is bind-mounted for hot reload, keep `node_modules` in a container-only volume. Native npm packages built for `darwin-arm64` do not work in a Linux container and vice versa.
- Vite inside a container must listen on all interfaces (`server.host`), and its `/api` proxy target must be the Compose service name (e.g. `http://backend:8080`) — `localhost` inside the frontend container is the container itself.
- Use only relative paths in `compose.yaml`; no `/Users/...`, `C:\...` or `~`.

## Line endings and file modes

- Root `.gitattributes` enforces LF (`* text=auto eol=lf`; `*.cmd`/`*.bat` CRLF). Do not weaken it. CRLF breaks `mvnw` and shell scripts inside Linux containers.
- `mvnw` and `*.sh` must be committed executable (`git update-index --chmod=+x <file>`).

## WSL specifics

- General advice: clone the repository inside the WSL Linux filesystem (e.g. `~/code/faircourt`); file access and file-change notifications across a Windows mount (`/mnt/c`, `/mnt/d`) are slow/unreliable. Exception (owner decision 2026-11-12): the owner's Windows machine keeps the repo on `/mnt/d` on purpose — do not move it or suggest moving it.
- Vite polling exists as an opt-in switch (`VITE_USE_POLLING=true` in `.env`, default `false`), added 2026-11-12 after hot reload was observed not to work on `/mnt/d`. Keep it opt-in; do not turn it on by default.
- Run `git`, `docker compose` and editors from inside WSL for this repo so line endings and permissions stay consistent.

## Working in parallel

- Code syncs **only via git** (commit/push on one machine, pull on the other). Never commit `.env`.
- Each machine has its own Postgres volume; data does not sync. The idempotent seed gives both the same baseline.
- Never run `docker compose down -v` or `docker volume rm` without the owner's explicit consent — it deletes the local database.
- Published host ports are `9090` (backend), `9091` (frontend), `9092` (JDWP debug) — owner decision 2026-11-12, to stay clear of the HRM project (8080, 8081, 1433, 6379, ...) — and `9093` (PostgreSQL on `127.0.0.1` only, for DBeaver; owner decision 2026-11-13). They are overridable via `BACKEND_PORT` / `FRONTEND_PORT` / `BACKEND_DEBUG_PORT` / `POSTGRES_PORT` in `.env`; container-internal ports stay 8080 / 5173 / 5005. Never use HRM ports; pick 9090+ if another port is needed. If a port conflicts, report it and ask.

## Definition of done for Docker-related changes

A Docker/Compose change is verified only after `docker compose up --build -d`, health UP and the public page loading **on each machine**. In the handoff, state per machine: `uname -m`, `docker version`, commands run, result — or "not verified on <machine>".

## Owner's Windows machine setup (for agents on the Mac)

The detailed Windows guide (`docs/guides/WSL_LOCAL_DEV.md`) is **local-only** on that machine (`docs/` is in its `.git/info/exclude`), so it never reaches the Mac. What matters here:

- All project work on Windows runs inside WSL Ubuntu; nothing is run with Windows tools. The repo stays on `D:\External\Personal\faircourt` (`/mnt/d/...`) by owner choice.
- Its `.env` adds `VITE_USE_POLLING=true` (no file events across `/mnt/d`), `BACKEND_DEBUG_OPTS=...address=*:5005` and `COMPOSE_FILE=compose.yaml:compose.backend-dev.yaml` (backend dev mode with DevTools).
- IntelliJ on Windows edits and compiles the backend (IDE-only JDK 25) and attaches to `localhost:9092` (host side of container port 5005); the app itself always runs in Docker.
- Keep these working when changing Docker files: do not remove the `VITE_USE_POLLING` switch, the `JAVA_TOOL_OPTIONS`/debug-port wiring, the `dev` Dockerfile stage or `compose.backend-dev.yaml` without asking the owner. All of them default to off, so the Mac's plain `docker compose up --build -d` is unaffected.
- Host port 5432 is occupied on the Windows machine. Postgres is published only on `127.0.0.1:${POSTGRES_PORT:-9093}` (owner decision 2026-11-13), never on 5432.
