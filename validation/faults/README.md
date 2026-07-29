# VAL-06 controlled outages

`run-outage.sh kafka|db|lnd` stops exactly one Docker Compose service for a
bounded interval, starts it again even on interruption via an EXIT trap, polls
service/application recovery, and records before/during/after evidence.

Both safety confirmations are mandatory:

```bash
ARATIRI_FAULT_CONFIRM=I_ACCEPT_NON_PRODUCTION_OUTAGE \
ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII \
PROBE_BEARER_TOKEN='synthetic-operator-token' \
OUTAGE_DURATION_SECONDS=60 \
validation/faults/run-outage.sh kafka
```

The runner refuses production, mainnet, and testnet. The default is regtest.
Regtest evidence remains provisional until D-05 is resolved; testnet is smoke
only and must not be used for outages. Kafka/DB default to `docker-compose.yml`.
LND defaults to `validation/regtest/compose.yml`, its
`runtime/compose.env`, and service `lnd-alice`; it never selects the provisional
root `docker-compose.lnd.yml`. For an approved alternative topology, set
`FAULT_COMPOSE_FILES` (colon-separated), `FAULT_ENV_FILE`, and `FAULT_SERVICE`
explicitly.

Optional `SERVICE_PROBE_URL` adds a subsystem-specific HTTP observation.
`HEALTH_URL`, `PROMETHEUS_URL`, `RECOVERY_TIMEOUT_SECONDS`, and
`RECOVERY_POLL_SECONDS` are configurable. Authenticated probes are the default
contract: `PROBE_BEARER_TOKEN` is required and the same token is applied to
health, Prometheus, subsystem, and recovery probes. Only deployments whose
Actuator probes are intentionally public may omit it, with the explicit
`PUBLIC_PROBES_CONFIRM=I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC` opt-out. The token
is not written to generated evidence or metadata; do not place credentials in
URLs.

Before any Compose stop command, the runner records a preflight health
observation and requires a 2xx response. A missing/expired token, incorrect URL,
or unhealthy baseline therefore refuses the outage instead of producing a
guaranteed 401/down result.

The generated summary deliberately remains
`INCONCLUSIVE_PENDING_CORRECTNESS_REVIEW`. Container state and HTTP recovery do
not prove:

- zero lost/duplicate ledger effects;
- an explainable terminal/unknown outcome;
- backlog drain to zero;
- retry count or webhook correctness;
- RPO/RTO compliance.

Correlate raw service/application logs, PostgreSQL state, Kafka lag, LND state,
and command/transaction/event IDs. Freeze RPO/RTO before the campaign; if the
runner is interrupted, verify the target service manually even though the trap
attempts recovery.
