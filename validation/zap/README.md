# VAL-08 authenticated OWASP ZAP

`run.sh` executes the pinned `ghcr.io/zaproxy/zaproxy:2.15.0` image. It requires
written authorization and injects a laboratory `Authorization` header through a
ZAP replacer rule. Secrets are supplied only as environment variables and are
not written to plans, metadata, coverage artifacts, or Docker command-line
arguments.

- `baseline` spiders and waits for passive scanning.
- `full` also runs an intrusive active scan and needs the additional
  `ZAP_CONFIRM_ACTIVE_SCAN=I_ACCEPT_INTRUSIVE_ACTIVE_SCAN` confirmation.
  It also requires explicit `ARATIRI_ENVIRONMENT` and `ARATIRI_NETWORK` values
  and refuses either value when it denotes production, mainnet, or testnet.

Example:

```bash
ZAP_TARGET_URL='https://staging.example.invalid' \
ZAP_BEARER_TOKEN='synthetic-user-token' \
ZAP_CONFIRM_AUTHORIZED_TARGET=I_HAVE_WRITTEN_AUTHORIZATION \
validation/zap/run.sh baseline
```

Full non-production example:

```bash
ZAP_TARGET_URL='https://staging.example.invalid' \
ZAP_BEARER_TOKEN='synthetic-user-token' \
ZAP_CONFIRM_AUTHORIZED_TARGET=I_HAVE_WRITTEN_AUTHORIZATION \
ZAP_CONFIRM_ACTIVE_SCAN=I_ACCEPT_INTRUSIVE_ACTIVE_SCAN \
ARATIRI_ENVIRONMENT=staging \
ARATIRI_NETWORK=regtest \
validation/zap/run.sh full
```

For a non-Bearer scheme, set the complete `ZAP_AUTH_HEADER_VALUE` in the
environment instead. HTTP is refused unless
`ZAP_ALLOW_HTTP_LAB=I_ACCEPT_HTTP_IN_ISOLATED_LAB` confirms an isolated lab.

OpenAPI is disabled by default in Aratiri, so the plans do not depend on a
discoverable specification. A ZAP `requestor` job deterministically seeds
`/v1/auth/me`, account, transaction, and admin routes with the same replacer
authentication. Before ZAP starts, the runner also sends authenticated GETs to
those routes and writes `authenticated-v1-coverage.jsonl` plus
`coverage-summary.json`. At least one `/v1` route must return 2xx; otherwise the
runner exits 3 and explicitly marks the attempt
`INCONCLUSIVE_NO_AUTHENTICATED_V1_COVERAGE`. `ZAP_API_ROUTES` may override the
comma-separated preflight list, but every entry must remain under `/v1/`.

Artifacts include JSON/HTML reports, console log, timestamps, exit code, commit,
mode, target, and the required authenticated coverage artifact. Correlate access
logs as an additional check: a 2xx preflight proves reachability, not complete
authenticated attack coverage. ZAP coverage does not prove authorization isolation, webhook replay
defense, secret rotation, absence of PII in logs, or remediation. The VAL-08
criterion is zero unresolved critical/high findings or an explicit approved
acceptance, not merely a zero exit code.
