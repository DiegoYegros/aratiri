# Aratiri deployment runbook

Push-to-deploy pipeline for the production server (`ubuntu-2gb-fsn1-1`,
Hetzner, 2 GB RAM, user `daya`, reachable over Tailscale at 100.124.162.6).

## How it works

1. Push to `master` → GitHub Actions (`deploy.yml`) runs the build gate,
   then builds the Docker image and pushes it to GHCR tagged `latest` and
   with the commit SHA. Frontend/admin repos do the same on `main`.
2. On the server, `aratiri-deploy.timer` (3 min) runs `ops/deploy/deploy.sh`:
   it compares the deployed image digests with GHCR `latest`, and only on
   change runs `docker compose pull && docker compose up -d` under a flock.
3. Postgres, Kafka and LND are pinned images with named volumes — the poller
   never recreates them unless their image changes.

## Server layout

- `/home/daya/aratiri-deploy/docker-compose.yml` — production compose
  (kept in sync with `ops/docker-compose.prod.yml` in this repo).
- `/home/daya/aratiri-deploy/.env` — runtime secrets (never committed).
- `ops/deploy/` — poller script + systemd units (installed at
  `~/.config/systemd/user` or system level as `aratiri-deploy.{service,timer}`).

## Resource limits (2 GB box — do not raise without checking RAM)

- Kafka: `KAFKA_HEAP_OPTS=-Xms256m -Xmx256m`
- Backend JVM: `JAVA_TOOL_OPTIONS=-Xmx384m`, Hikari pool 10
- Gradle (local/CI low-mem builds): `-Xmx1024m`, `workers.max=1`

## Manual operations

```bash
# status
ssh daya@100.124.162.6 'docker compose -f ~/aratiri-deploy/docker-compose.yml ps'

# force a poll now
ssh daya@100.124.162.6 '~/aratiri-deploy/ops/deploy/deploy.sh'

# dry run (what a poll would do)
ssh daya@100.124.162.6 '~/aratiri-deploy/ops/deploy/deploy.sh --dry-run'

# timer state + logs
ssh daya@100.124.162.6 'systemctl list-timers aratiri-deploy.timer; journalctl -u aratiri-deploy.service -n 50'
```

## Rollback

Pin the previous image SHA in the compose file (`image: ghcr.io/...@sha256:...`),
then `docker compose up -d`. SHAs are visible in the GHCR package page and in
this repo's CI runs.
