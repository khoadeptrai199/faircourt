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
| MacBook Pro | macOS 15.1, `arm64`, Docker 28.0.4, Compose v2.34 (Docker Desktop) | — |
| Windows PC | Uses WSL | CPU arch (`uname -m`), Docker Desktop WSL integration vs Docker Engine inside WSL, Docker/Compose versions |

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

- Clone the repository inside the WSL Linux filesystem (e.g. `~/code/faircourt`), not under `/mnt/c/...`. File access and file-change notifications across the Windows mount are slow/unreliable.
- Only if hot reload is observed not to work, consider Vite `server.watch.usePolling` — do not add it preemptively.
- Run `git`, `docker compose` and editors from inside WSL for this repo so line endings and permissions stay consistent.

## Working in parallel

- Code syncs **only via git** (commit/push on one machine, pull on the other). Never commit `.env`.
- Each machine has its own Postgres volume; data does not sync. The idempotent seed gives both the same baseline.
- Never run `docker compose down -v` or `docker volume rm` without the owner's explicit consent — it deletes the local database.
- Published ports `5173` and `8080` must be free on both machines; if a port conflicts, report it and ask before changing the contract ports.

## Definition of done for Docker-related changes

A Docker/Compose change is verified only after `docker compose up --build -d`, health UP and the public page loading **on each machine**. In the handoff, state per machine: `uname -m`, `docker version`, commands run, result — or "not verified on <machine>".
