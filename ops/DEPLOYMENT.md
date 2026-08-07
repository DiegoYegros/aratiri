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

## LND (Bitcoin mainnet)

Runtime config lives in `~/aratiri-deploy/lnd-data/lnd.conf` (bind-mounted).
Keep production on mainnet with a public peer URI:

- `bitcoin.mainnet=true` (not testnet/regtest), `bitcoin.node=neutrino`
- `externalip=<server public IPv4>` under `[Application Options]` so
  `lncli getinfo` reports `uris` like `pubkey@<ip>:9735`
- Optional `[Neutrino]` `neutrino.addpeer=...` lines for mainnet peers

Backend secrets at `~/aratiri-deploy/secrets/` must match the live node:

- `admin.macaroon` — **hex-encoded** (`xxd -p … | tr -d '\n'`), not raw
  binary; must decode to the same bytes as
  `lnd-data/data/chain/bitcoin/mainnet/admin.macaroon`
- `tls.cert` — byte-identical to `lnd-data/tls.cert`

After rotating either file, `docker compose restart aratiri-app` so the
read-only `/run/secrets` mount is re-read.

## Network isolation (supply-chain / botnet hardening)

Compose defines four networks so compromised dependencies cannot freely scan
the internet or the data plane:

| Network | `internal` | Members | Purpose |
| --- | --- | --- | --- |
| `aratiri_data` | yes | postgres, kafka, lnd, backend | East-west only (`db`, `kafka:29092`, `lnd:10009`) |
| `aratiri_edge` | no\* | frontend, admin | FE/admin only (no data-plane peers). \*`internal:true` breaks host port publish; NEW internet egress is DROPped by `ops/apply-edge-egress-block.sh` |
| `aratiri_backend_egress` | no | backend only | SMTP, currency APIs, LNURL, Nostr, webhooks |
| `aratiri_lnd_egress` | no | lnd only | Lightning/Bitcoin peer traffic (`:9735`) |

App HTTP binds loopback only (Cloudflare Tunnel → Caddy):
`127.0.0.1:2100` (backend), `127.0.0.1:3100` (frontend), `127.0.0.1:3101` (admin).

Browser clients still call `https://api.aratiri.net` (Cloudflare Tunnel → Caddy
→ `127.0.0.1:2100`). Frontend containers do not need to reach the backend.

Backend host publish stays loopback-only (`127.0.0.1:2100`). Unauthenticated
process probes are intentional for ops:

- `GET /actuator/health` — liveness/readiness (status-only JSON; no details)
- `GET /actuator/prometheus` — Prometheus scrape text

`/actuator/metrics`, `/actuator/info`, and other actuator routes stay JWT-authenticated.
Public scrape must be denied at the edge (Caddy), not relied on for JWT 401 alone.

### Caddy: deny public actuator scrape on `api.aratiri.net`

Add (or keep) a deny before the reverse_proxy so Cloudflare/internet cannot
scrape metrics. Prefer `respond 403` (or `404`) for these paths:

```caddy
api.aratiri.net {
	# Deny public metrics scrape; scrape only via Tailscale/SSH loopback.
	@deny_actuator path /actuator/prometheus /actuator/metrics /actuator/metrics/*
	respond @deny_actuator 403

	reverse_proxy 127.0.0.1:2100
}
```

Prometheus scrape path (server-local / Tailscale SSH tunnel):

```text
http://127.0.0.1:2100/actuator/prometheus
```


After every deploy (and on boot via systemd), re-apply the edge egress block then verify:

```bash
# install once on the server
sudo cp ~/aratiri-deploy/ops/aratiri-edge-egress-block.service \
        ~/aratiri-deploy/ops/aratiri-edge-egress-block.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now aratiri-edge-egress-block.timer

# manual / verify
sudo ~/aratiri-deploy/ops/apply-edge-egress-block.sh
~/aratiri-deploy/ops/verify-network-isolation.sh
```

The timer re-runs the block every 5 minutes so recreating `aratiri_edge` cannot leave FE/admin with open NAT for long.

Residual risk: a compromised **backend** JAR still has intentional egress.
User-influenced HTTP is additionally gated by `OutboundDestinationPolicy`.
Optional hardening: host `DOCKER-USER` rules blocking RFC1918/metadata from
Docker bridge CIDRs.

## Email (register / password reset)

Production must set non-empty `EMAIL_USERNAME` and `EMAIL_PASSWORD` in
`~/aratiri-deploy/.env` (mapped into the backend container). Without both,
`POST /v1/auth/register` and password-reset initiation fail closed with HTTP
503 ("Email delivery is not configured") instead of pretending mail was sent.
Do not invent or commit SMTP secrets — use real provider credentials only.

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

## One-time server cutover (local tags → GHCR)

`deploy.sh` fail-closes before pull/redeploy if any Aratiri app `image:` in the
active compose file is not a qualified
`ghcr.io/diegoyegros/{aratiri,aratiri-frontend,aratiri-admin}` ref (tag or
`@sha256:` pin). Soft-fail on `compose pull` remains only for those valid GHCR
refs when the registry is unreachable or auth fails.

If `~/aratiri-deploy/docker-compose.yml` still uses local/unqualified tags
(e.g. `aratiri-admin:…`), cut over once:

```bash
# 1. Replace the three app image: lines with GHCR refs from this repo's
#    ops/docker-compose.prod.yml:
#      ghcr.io/diegoyegros/aratiri:latest
#      ghcr.io/diegoyegros/aratiri-frontend:latest
#      ghcr.io/diegoyegros/aratiri-admin:latest
#    Keep container_name, ports (127.0.0.1:{2100,3100,3101}), env, and volumes.

# 2. Sync the poller from this repo
mkdir -p ~/aratiri-deploy/ops
cp -r ops/deploy ~/aratiri-deploy/ops/

# 3. Validate without touching containers
~/aratiri-deploy/ops/deploy/deploy.sh --dry-run

# 4. Enable / recover the timer
sudo systemctl enable --now aratiri-deploy.timer
sudo systemctl reset-failed aratiri-deploy.service || true
```

## Rollback

Pin the previous image SHA in the compose file (`image: ghcr.io/...@sha256:...`),
then `docker compose up -d`. SHAs are visible in the GHCR package page and in
this repo's CI runs.

For a Tailscale-only Prometheus + Grafana pack (bind
`100.124.162.6:9090` / `:3002`, scrape the loopback actuator above), see
[`ops/observability/`](observability/) and its README
(`~/aratiri-observability` on the server). Do not expose those UIs on the
public internet or through Caddy/Tunnel.

## Re-enable continuous deploy

The timer may be left enabled while GHCR `:latest` lags newer locally-loaded
Batch images: place `~/aratiri-deploy/.deploy.hold` (or set
`ARATIRI_DEPLOY_HOLD=1` in the unit env). `deploy.sh` then logs
`deploy hold active; skipping` and exits 0 without `compose pull`.

GHCR push from the server currently fails with `permission_denied` scopes — requires a PAT/`write:packages` before clearing `.deploy.hold`.

When GHCR is current and continuous deploy should resume:

1. **Publish current images to GHCR** (either path):
   - Merge `feat/orch-batch-2` → `master`/`main` so CI (`deploy.yml`,
     `permissions: packages: write`) pushes `:latest`, **or**
   - From a host that has the local Batch images:
     `ops/deploy/publish-images.sh <aratiri-ref> <frontend-ref> <admin-ref>`
     with a PAT that has `write:packages` (push fails closed on
     `permission_denied` / insufficient scope).
2. **Server auth for pull** — put `GHCR_USERNAME` / `GHCR_TOKEN` in
   `~/aratiri-deploy/.env` if packages are still private (read is enough for
   the poller; write is only needed for `publish-images.sh`).
3. **Clear the hold** — remove `~/aratiri-deploy/.deploy.hold` and unset
   `ARATIRI_DEPLOY_HOLD` if set.
4. **Enable the timer**:
   `sudo systemctl enable --now aratiri-deploy.timer`
5. **Verify**:
   ```bash
   ~/aratiri-deploy/ops/deploy/deploy.sh --dry-run
   journalctl -u aratiri-deploy.service -n 50
   ```

Do not invent or commit GHCR tokens.
