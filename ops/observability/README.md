# Aratiri observability (Prometheus + Grafana)

Tailscale-only metrics pack for the production host. Scrapes the loopback
actuator already exposed by the Aratiri backend:

```text
http://127.0.0.1:2100/actuator/prometheus
```

Public internet must not reach these UIs. Prometheus and Grafana listen on the
host Tailscale IP only (`100.124.162.6:9090` / `:3002`). Caddy continues to
deny public `/actuator/prometheus` (see `ops/DEPLOYMENT.md`).

Images are from Docker Hub (`prom/prometheus`, `grafana/grafana`) — no GHCR
dependency. This stack is separate from `~/aratiri-deploy`.

## Memory budget

| Service    | Limit  | Notes                                      |
| ---------- | ------ | ------------------------------------------ |
| Prometheus | 384 Mi | 15d / 512 MB TSDB retention                |
| Grafana    | 384 Mi | Host network, port 3002                    |

Keep both modest: the box already runs Aratiri + Caddy + other services.

## Layout on the server

```text
~/aratiri-observability/
  docker-compose.yml
  prometheus.yml
  .env                 # real secrets (from .env.example)
  grafana/
    provisioning/
    dashboards/
```

## Install (once)

From a checkout of this repo (or copy the `ops/observability/` tree):

```bash
mkdir -p ~/aratiri-observability
cp -a ops/observability/. ~/aratiri-observability/
cd ~/aratiri-observability
cp .env.example .env
# edit .env — set a strong GRAFANA_ADMIN_PASSWORD
# confirm TAILSCALE_IP=100.124.162.6 matches `tailscale ip -4`

docker compose --env-file .env config   # validate
docker compose --env-file .env up -d
docker compose ps
```

## Open Grafana

On a machine joined to the same Tailscale tailnet:

```text
http://100.124.162.6:3002
```

Login: `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env`
(defaults in `.env.example` are placeholders only).

Prometheus UI (same Tailscale constraint):

```text
http://100.124.162.6:9090
```

## Verify scrape is UP

```bash
# From the Tailscale host (or any tailnet peer):
curl -sS 'http://100.124.162.6:9090/api/v1/targets' \
  | grep -E '"health":"(up|down)"|aratiri|/actuator/prometheus'

# Expect health "up" for job aratiri → 127.0.0.1:2100/actuator/prometheus

# Optional: confirm actuator still answers on loopback
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:2100/actuator/prometheus
# expect 200
```

In Grafana: **Connections → Data sources → Prometheus → Save & test**, then open
dashboard **Aratiri JVM / HTTP** (folder Aratiri).

## Why `network_mode: host`

The backend publishes HTTP as `127.0.0.1:2100` only. A bridge-network container
cannot scrape that loopback bind via `host.docker.internal` / host-gateway.
Prometheus therefore uses host networking and scrapes `127.0.0.1:2100`
directly, with `--web.listen-address=<TAILSCALE_IP>:9090` so the UI is not on
`0.0.0.0`. Grafana also uses host networking and
`GF_SERVER_HTTP_ADDR` / `GF_SERVER_HTTP_PORT=3002` for the same reason
(avoids clashing with cajubi `:3000` and the FE `:3100`).

## Security notes

- Do not publish Prometheus/Grafana on the public NIC or Cloudflare Tunnel.
- Do not weaken the Caddy deny for `/actuator/prometheus`.
- Do not change Spring Security allowlists for this pack.
- Rotate `GRAFANA_ADMIN_PASSWORD` after first login if the example value was used.

## Stop / remove

```bash
cd ~/aratiri-observability
docker compose --env-file .env down
# add -v only if you intend to wipe Prometheus/Grafana volumes
```
