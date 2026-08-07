# Aratiri observability (Prometheus + Loki + Grafana)

Tailscale-only metrics + logs pack for the production host. Prometheus scrapes
the loopback actuator already exposed by the Aratiri backend; Promtail ships
the `aratiri-backend` container's json-file logs into Loki so they are
searchable in Grafana Explore by traceId / userId / path / message:

```text
http://127.0.0.1:2100/actuator/prometheus          # metrics (scrape target)
{container="aratiri-backend"} | json | trace_id=~"…"   # logs (Loki)
```

Public internet must not reach these UIs. Prometheus, Grafana and Loki listen
on the host Tailscale IP only (`100.124.162.6:9090` / `:3002` / `:3100`);
Promtail listens on loopback (`127.0.0.1:9080`, no UI). Note the frontend /
admin bind `127.0.0.1:3100` / `:3101` on the same host — no clash with Loki,
which binds the Tailscale address.

Images are from Docker Hub (`prom/prometheus`, `grafana/grafana`,
`grafana/loki`, `grafana/promtail`) — no GHCR dependency. This stack is
separate from `~/aratiri-deploy`; only Promtail touches another compose stack:
it reads `aratiri-backend`'s docker logs via the socket.

## Memory budget

| Service    | Limit  | Notes                                      |
| ---------- | ------ | ------------------------------------------ |
| Prometheus | 384 Mi | 15d / 512 MB TSDB retention                |
| Grafana    | 384 Mi | Host network, port 3002                     |
| Loki       | 256 Mi | Single binary, filesystem storage, 14d      |
| Promtail   | 128 Mi | Docker SD, ships `aratiri-backend` logs      |
| **Total**  | 1,152 Mi | Loki + Promtail = 384 Mi (≤ 550 Mi cap)     |

Loki + Promtail stay under 550 Mi combined (256 + 128 = 384 Mi) as the budget
calls for; the whole observability pack is ~1.1 GiB of the 2 GiB box. Keep the
app stack modest too — see `ops/DEPLOYMENT.md` resource limits.

## Layout on the server

```text
~/aratiri-observability/
  docker-compose.yml
  prometheus.yml
  .env.example          # template — copy to .env (never commit .env)
  .env                   # real secrets (from .env.example)
  loki/
    loki.yaml
  promtail/
    promtail.yml
  grafana/
    provisioning/
      datasources/       # prometheus + loki
      dashboards/
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

Promtail needs the docker socket at `/var/run/docker.sock` (read-only mount)
for Docker service discovery. giving it the socket is the standard trade.
The socket is mounted `:ro` and Promtail ships only the `aratiri-backend`
container's logs.

## Open Grafana

On a machine joined to the same tailnet:

```text
http://100.124.162.6:3002
```

Login: `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` from `.env`
(defaults in `.env.example` are placeholders only).

Prometheus UI (same Tailscale constraint):

```text
http://100.124.162.6:9090
```

## Search logs in Grafana Explore

**Explore → data source Loki** (`uid: loki`, provisioned). The `level` and
`stream` labels are index labels; every other field (`traceId`, `requestId`,
`userId`, `path`, `message`, … ) is a top-level JSON key in each Logstash line,
re-derived by the `| json` parser at query time. Queries:

```logql
# Any backend log (start here)
{container="aratiri-backend"}

# By traceId (echoed request id — see the X-Request-Id response header)
{container="aratiri-backend"} | json | traceId="<requestId-from-response-header>"

# By userId (principal in the MDC on authenticated requests)
{container="aratiri-backend"} | json | userId="<userId>"

# By level
{container="aratiri-backend"} | json | level="ERROR"

# By message text / path
{container="aratiri-backend"} |= "payment"
{container="aratiri-backend"} | json | path=~"/v1/payments.*"
```

Bursts by requestId are not in the label set — query them the same way
(`| json | requestId="…"`). Never promote traceId/userId to labels: high
cardinality defeats Loki.

## Verify Loki is up

```bash
# From the Tailscale host (or any tailnet peer):
curl -sS -o /dev/null -w '%{http_code}\n' http://100.124.162.6:3100/ready
# expect 200

# Confirm promtail is connected and shipping (server self-metrics):
curl -sS 'http://127.0.0.1:9080/metrics' | grep -E 'promtail_ready|promtail_read_bytes_total|promtail_files_active_total'
# expect promtail_ready... 1 and active files >= 1

# End-to-end: ask Loki for the newest backend log line
curl -sS -G 'http://100.124.162.6:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={container="aratiri-backend"}' \
  --data-urlencode 'limit=5' | grep -o '"level":"' | head -1

# Prometheus scrape health (existing):
curl -sS 'http://100.124.162.6:9090/api/v1/targets' \
  | grep -E '"health":"(up|down)"|aratiri|/actuator/prometheus'
```

In Grafana: **Connections → Data sources → Loki → Save & test**, then open
**Explore** and select Loki as source.

## Why `network_mode: host`

The backend publishes HTTP as `127.0.0.1:2100` only. A bridge-network container
cannot scrape that loopback bind via `host.docker.internal` / host-gateway.
Prometheus therefore uses host networking and scrapes `127.0.0.1:2100`
directly. Grafana uses host networking with `GF_SERVER_HTTP_ADDR` / port 3002;
Loki binds the Tailscale address on 3100; Promtail targets loopback:9080 (it
pulls in logs via the socket, then pushes to Tailscale Loki). Nothing binds
0.0.0.0.

## Security notes

- Do not publish these UIs on the public NIC or Cloudflare Tunnel; keep
  Caddy's deny for `/actuator/prometheus`.
- Do not change Spring Security allowlists for this pack.
- Rotate `GRAFANA_ADMIN_PASSWORD` after first login if the example value was used.
- `.env` is server-only — never commit real passwords / Tailscale secrets;
  keep `docker compose --env-file .env` everywhere.

## Stop / remove

```bash
cd ~/aratiri-observability
docker compose --env-file .env down
# add -v also wipes Prometheus/Grafana/Loki data volumes
```