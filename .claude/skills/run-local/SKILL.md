---
name: run-local
description: Start, stop, inspect or health-check the FairCourt local stack with Docker Compose on macOS (Apple Silicon) or Windows WSL, following blueprint §0. Never deletes the database volume without explicit owner consent.
argument-hint: "[up | down | status | logs <service> | health]"
---

# /run-local

Action requested: **$ARGUMENTS** (default: `up`)

All commands run from the repository root. Follow `.claude/rules/docker-crossplatform.md`.

## 0. Preflight (every action)

```bash
uname -s; uname -m
docker version --format '{{.Server.Version}}'
docker compose version
```

Report which machine this is (macOS arm64 / WSL + arch). If the Docker daemon is not reachable, stop and tell the owner — do not try to install or reconfigure Docker.

If `compose.yaml` does not exist yet, say so and stop: the stack has not been implemented.

## Actions

### up

1. If `.env` is missing: `cp .env.example .env`. If `.env` already exists, **never overwrite it**.
2. `docker compose up --build -d`
3. Wait for health (poll, max ~3 minutes):
   - `docker compose ps` — all services running / healthy
   - `curl -fsS http://localhost:9090/actuator/health` → contains `"status":"UP"`
   - `curl -fsS -o /dev/null -w '%{http_code}' http://localhost:9091/` → `200`
4. Print the sample public code from the startup logs (`docker compose logs playmatch-service | grep "public code"`); the link is `http://localhost:9091/s/<code>`. Do not make one up. (`README.md` is empty by owner request.)

### down

`docker compose down` — keeps the DB volume (§0).

### status

`docker compose ps` and the two health checks above.

### logs

`docker compose logs --tail=200 <service>` (`postgres`, `gateway`, `auth-service`, `player-service`, `playmatch-service`, `frontend`). Several at once: `docker compose logs --tail=200 gateway playmatch-service`.

### One service (start / rebuild / stop)

- Start only one service (PostgreSQL starts with it): `docker compose up -d <service>`
- Rebuild and restart one service after a code change: `docker compose up -d --build <service>`
- Stop one service, others keep running: `docker compose stop <service>`

### health

Only the health checks above, no start/stop.

## Reset demo data — requires explicit consent

`docker compose down -v` **deletes the local database**. Never run it (or `docker volume rm`) unless the owner explicitly says so in this conversation. Ask first, naming exactly what will be lost.

## Report

```text
Machine: <os/arch>   Docker: <version>   Compose: <version>
Commands run: <list with exit status>
postgres: <state>  gateway health: <UP/...>  auth/player/playmatch: <healthy/...>  frontend: <HTTP code>
Local URL: http://localhost:9091
Sample public link: <from playmatch-service logs, or "not found">
Not verified: <anything skipped>
```

On failure: show the relevant log lines, state the likely cause only if the logs support it, and propose a fix — do not apply changes outside running the stack without asking.
