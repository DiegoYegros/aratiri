# VAL-04 load profile

`run.sh` uses the pinned `grafana/k6:0.52.0` image; no local k6 install is
required. It creates four sequential `constant-vus` scenarios:

| Plateau | VUs | Duration |
| --- | ---: | ---: |
| 1 | 10 | 10 minutes |
| 2 | 25 | 10 minutes |
| 3 | 50 | 10 minutes |
| 4 | 100 | 10 minutes |

The default `transactions` workload performs authenticated
`GET /v1/transactions?limit=50`. `currencies` selects
`GET /v1/general-data/currencies`. `custom` uses `REQUEST_METHOD`,
`REQUEST_PATH`, and optional `REQUEST_BODY`.

A `custom` non-GET/HEAD/OPTIONS workload is mutation load and is refused unless
both `ARATIRI_LOAD_CONFIRM=I_ACCEPT_NON_PRODUCTION_MUTATION_LOAD` and
`ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII` are set. It is always
rejected on production, mainnet, and testnet.

Important variables:

| Variable | Default | Meaning |
| --- | --- | --- |
| `BASE_URL` | `http://host.docker.internal:2100` | Target origin |
| `TOKEN` | empty | Synthetic-user bearer token |
| `WORKLOAD` | `transactions` | `transactions`, `currencies`, or `custom` |
| `EXPECTED_STATUSES` | `200` | Comma-separated accepted HTTP statuses |
| `MAX_ACCEPTANCE_P95_MS` | `500` | Acceptance threshold, milliseconds |
| `MAX_ERROR_RATE` | `0.01` | Provisional error-rate threshold |
| `MIN_THROUGHPUT_RPS` | `1` | Provisional minimum acceptance requests/s |
| `THINK_TIME_SECONDS` | `0.2` | Per-VU pause |
| `STEP_DURATION` | `10m` | Dry-run override; final profile must remain `10m` |

The 1% error rate and 1 req/s throughput defaults are runner safeguards, not
approved research findings. Freeze them with stakeholders before the final
campaign and record any override.

Set `POLL_TERMINAL=true` for an asynchronous command workload. The acceptance
response ID is read from `ACCEPTANCE_ID_PATH` (default `transactionId`), then
`SETTLEMENT_PATH_TEMPLATE` (default `/v1/transactions/{id}`) is polled until
`SETTLEMENT_STATE_PATH` (default `status`) is `COMPLETED` or `FAILED`.
`SETTLEMENT_TIMEOUT_SECONDS`, `POLL_INTERVAL_SECONDS`, and
`MAX_SETTLEMENT_ERROR_RATE` are configurable.

`terminal_observation_latency_ms` records either public terminal state.
`successful_settlement_latency_ms` records only `COMPLETED`. An observed
`FAILED` is terminal evidence but increments `terminal_settlement_errors`;
timeouts and missing IDs do likewise. `UNKNOWN_OUTCOME` is an internal
node-operation state, not a public `TransactionStatus`, and is not treated as a
terminal API state.

The output contains:

- `raw.json`: k6 point-level JSON output;
- `summary.json`: machine-readable k6 summary including p50/p95/p99;
- `run-metadata.json`: commit, non-secret profile, and start timestamp;
- `ended-at.txt` and `runner-exit-code.txt`.

`api_acceptance_latency_ms` measures request to HTTP response only.
The terminal/success metrics, when enabled, measure from acceptance to observed
terminal state. Do not describe acceptance as Bitcoin/Lightning settlement
latency. A load result does not by itself prove correct ledger,
idempotency, Kafka/DB health, or absence of duplicate effects; correlate those
artifacts separately.
