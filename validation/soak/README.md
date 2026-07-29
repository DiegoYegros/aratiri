# VAL-09 48-hour soak

`run.sh` defaults to 172,800 seconds (48 hours), samples once per minute, makes
one low-traffic API probe per sample, and schedules a 20-request burst each
hour. It records:

- Actuator health body/status;
- Prometheus snapshots (gzip compressed);
- configured Docker container state;
- PostgreSQL outbox/node-operation backlog counts;
- Kafka consumer-group lag output;
- API probe/burst HTTP status and latency;
- commit, dirty-worktree flag, non-secret configuration, and UTC timestamps.

Every probe is isolated: health, metrics, Docker, DB, or Kafka failure is
recorded and the next sample still runs. SIGINT/SIGTERM produces a partial
machine-readable `summary.json`. `samples.jsonl` is the sample index; detailed
raw observations remain under `samples/`.

Required confirmations:

```bash
ARATIRI_SOAK_CONFIRM=RUN_48H_NON_PRODUCTION \
ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII \
PROBE_BEARER_TOKEN='synthetic-operator-token' \
validation/soak/run.sh
```

Useful variables include `BASE_URL`, `HEALTH_URL`, `PROMETHEUS_URL`,
`API_PROBE_URL`, `PROBE_BEARER_TOKEN`, `CONTAINERS`, `DB_CONTAINER`,
`KAFKA_CONTAINER`, `SAMPLE_INTERVAL_SECONDS`, `BURST_INTERVAL_SECONDS`, and
`BURST_REQUESTS`. Authenticated probes are the default contract:
`PROBE_BEARER_TOKEN` is required and applied to health, Prometheus, API, and
burst probes. Only deployments whose Actuator/API probes are intentionally
public may omit it, with
`PUBLIC_PROBES_CONFIRM=I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC`. The token is used
from the environment and is never written to evidence. Do not embed credentials
in URLs.

Before entering the 48-hour loop, the runner records an initial health
observation and requires a 2xx response. An invalid token, wrong URL, or
unhealthy baseline therefore refuses the soak instead of recording a guaranteed
401/down campaign.

The default container allowlist is the controllable root stack:
`aratiri-backend,postgres_db,kafka`. Set `CONTAINERS` explicitly for another
approved topology. Container evidence is rendered through a fixed Docker
template containing only ID, name, image reference, state/health timestamps,
exit code, and restart count; it deliberately excludes `Config.Env`, mounts,
labels, and other secret-bearing inspection fields.

`SOAK_DURATION_SECONDS` exists for a documented dry run; a shorter run cannot be
reported as VAL-09. The calculated availability is healthy samples / total
samples, so disclose interval blind spots and observer failures. A completed
48-hour runner still requires incident review, resource-trend analysis, backlog
drain/correctness checks, frozen availability criteria, and an approved result
manifest. Regtest remains provisional pending D-05; testnet is smoke only and
is rejected by this runner.
