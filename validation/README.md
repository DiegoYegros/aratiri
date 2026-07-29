# Aratiri validation evidence toolkit

This directory contains reproducible runners for the quantitative experiments in
the PFGR validation protocol. The scripts are **evidence generators**, not
evidence by themselves: a VAL item remains `NOT_RUN` until its runner has
actually been executed against an approved, frozen environment and its raw
artifacts have been reviewed.

## Scope and safety

- `k6/` implements VAL-04 and can optionally record terminal settlement latency.
- `zap/` implements the authenticated passive/full DAST portions of VAL-08.
- `faults/` implements controlled Kafka, LND, and PostgreSQL outages for VAL-06.
- `soak/` implements the 48-hour observation runner for VAL-09.
- `templates/` supplies the result manifest, JSON Schema, and initial
  VAL-to-RF/RNF mapping.

Use only synthetic accounts, laboratory JWTs, and non-production infrastructure.
Never use real funds, real customer PII, production credentials, or mainnet.
Secrets are accepted through environment variables and are deliberately omitted
from metadata and result manifests.

The intended primary E2E environment is a controlled two-node **regtest**, but
that environment and its results are provisional until decision D-05 (the
approved ficha's “exclusively testnet” wording) is resolved. **Testnet is smoke
only**: do not run load, chaos, or a soak that spends funds there. Staging must
contain no real funds or real PII.

Fault and soak probes require `PROBE_BEARER_TOKEN` by default. Only an
intentionally public probe deployment may use the documented
`PUBLIC_PROBES_CONFIRM` opt-out. Mutation load and every outage/soak remain
confirmation-gated and are refused on production/mainnet/testnet as documented
by each runner.

## Before a campaign

1. Resolve/freeze the environment, commit, non-secret configuration, profile,
   sample size, error-rate/throughput criteria, and RPO/RTO with stakeholders.
2. Synchronize clocks and verify Actuator/Prometheus, Docker, database, Kafka,
   LND, application logs, and correlation IDs are observable.
3. Create a result manifest from
   `templates/result-manifest.template.yaml`; do not edit criteria after seeing
   results without recording the deviation.
4. Put generated artifacts under each tool's `results/` directory. These paths
   are gitignored; preserve them in the approved evidence store and record
   checksums/URIs in the manifest.

## Quick reference

Load (four exact 10-minute plateaus at 10/25/50/100 VUs):

```bash
TOKEN='laboratory-jwt' \
BASE_URL='https://staging.example.invalid' \
WORKLOAD=transactions \
validation/k6/run.sh
```

Authenticated ZAP passive scan:

```bash
ZAP_TARGET_URL='https://staging.example.invalid' \
ZAP_BEARER_TOKEN='laboratory-jwt' \
ZAP_CONFIRM_AUTHORIZED_TARGET=I_HAVE_WRITTEN_AUTHORIZATION \
validation/zap/run.sh baseline
```

Controlled outage (example Kafka, default 60 seconds):

```bash
ARATIRI_FAULT_CONFIRM=I_ACCEPT_NON_PRODUCTION_OUTAGE \
ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII \
PROBE_BEARER_TOKEN='synthetic-operator-token' \
validation/faults/run-outage.sh kafka
```

Soak (defaults to 48 hours):

```bash
ARATIRI_SOAK_CONFIRM=RUN_48H_NON_PRODUCTION \
ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII \
PROBE_BEARER_TOKEN='synthetic-operator-token' \
validation/soak/run.sh
```

Each subdirectory documents its variables, output, and limitations. A successful
tool exit is not automatically a PFGR `Cumple`: evaluate the frozen criteria,
raw artifacts, deviations, incidents, correctness/duplicate-effect checks, and
environment limitations in the result manifest.
