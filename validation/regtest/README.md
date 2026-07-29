# Aratiri regtest validation lab

This directory is a test-only Bitcoin/Lightning validation lab:

- Bitcoin Core `27.1`, pinned by image digest
- two independent LND `v0.18.5-beta` nodes, pinned by image digest
- one private channel with outbound capacity in both directions
- direct Lightning and on-chain smoke checks
- a separate real-LND Aratiri smoke path

The LND version deliberately matches the release represented by Aratiri's pinned protobuf API. The lab is isolated on an internal Docker network; only the two LND gRPC/REST endpoints bind to host loopback. It never uses real funds or personal data.

All node data, generated RPC credentials, macaroons, TLS material, and evidence are created under the gitignored `runtime/` directory. LND's `--noseedbackup` mode is designed only for test networks: it creates and auto-unlocks fresh wallets without printing or committing seed words. Never reuse this configuration on testnet or mainnet.

## Evidence status

Every result from this lab is **PROVISIONAL REGTEST EVIDENCE pending tutor approval of D-05**. It demonstrates deterministic local behavior, not public-testnet operation or production readiness.

## Prerequisites

- Docker with Compose
- Bash, `curl`, `jq`, and `od`
- Linux `amd64` (the image manifests are digest-pinned for that platform)

The full Aratiri smoke additionally requires the normal PostgreSQL and Kafka services, a migrated database containing the documented seeded local users, and Aratiri running on the host.

## Validate without starting containers

```bash
validation/regtest/scripts/verify.sh
```

This creates only gitignored local credentials, runs `bash -n`, and runs `docker compose config --quiet`. It does not pull images or start containers.

## Bootstrap

```bash
validation/regtest/scripts/bootstrap.sh
```

Bootstrap is safe to rerun. It:

1. starts the three digest-pinned containers;
2. waits for the `--noseedbackup` test wallets to be created and unlocked;
3. creates a Bitcoin Core miner wallet if absent and matures regtest coinbase outputs;
4. funds both LND wallets;
5. connects Alice and Bob;
6. opens one 2,000,000-sat channel with a 750,000-sat push, if no channel exists;
7. mines confirmations and waits for the channel to become active;
8. exports Alice's TLS certificate and a hex-encoded admin macaroon for Aratiri.

Each node's funding journal is bound to its current LND identity pubkey. It prevents a rerun from duplicating a prepared or unconfirmed funding transfer, including recovery after interruption between Bitcoin Core broadcast and journal update. A confirmed journal is not treated as permanent proof of sufficient funds: bootstrap re-reads the live total wallet balance on every run, archives the completed cycle if that balance has fallen below the lab threshold, and starts a new funding cycle. A journal from another wallet identity is archived and never reused.

The generated app settings are at:

```text
validation/regtest/runtime/export/aratiri.env
```

Merge that file with the normal local Aratiri database, Kafka, JWT, mail, CORS, and base-URL environment. It contains only the generated LND paths and endpoint needed to point a host `bootRun` process at Alice. Restart Aratiri after changing the LND settings.

## Direct node smoke

```bash
validation/regtest/scripts/smoke-direct.sh
```

This pays an Alice invoice from Bob, verifies both payer `SUCCEEDED` and invoice `SETTLED`, rejects a replay at LND, then sends 50,000 sats on-chain from Alice to Bob, mines six blocks, and verifies the exact confirmed balance delta.

## Aratiri real-LND smoke

Start the normal local database and Kafka services. Source the usual application environment, merge the generated LND settings, and restart the app. For example:

```bash
set -a
source .env
source validation/regtest/runtime/export/aratiri.env
set +a
./gradlew bootRun
```

In another terminal:

```bash
validation/regtest/scripts/smoke-aratiri.sh
```

The runner uses only the documented seeded users:

- `alice@example.com` / `password123`
- `bob@example.com` / `password123`

It first proves Aratiri is using Alice LND by creating an API invoice and finding it on that node. Bob LND pays the invoice to give the seeded Bob account a real append-only ledger credit. The runner then:

- submits an external Lightning debit and records HTTP `202/PENDING`;
- replays the same idempotency key and requires the same transaction ID;
- requires HTTP `409` when the same key is reused with a different request;
- polls the transaction API until `COMPLETED`;
- separately verifies the remote invoice is `SETTLED`;
- requires exactly one successful Alice LND payment effect;
- submits and replays an on-chain debit;
- distinguishes `202/PENDING` from terminal broadcast completion;
- mines confirmations and requires exactly one 15,000-sat destination balance increase.

If Aratiri is unavailable, is pointed at the wrong LND, lacks the seeded users, or cannot drive the asynchronous DB/Kafka workers to a terminal state, the script exits non-zero with explicit prerequisites. It never treats a `202 Accepted` response as settlement and never fabricates a passing result.

Override the API URL only when needed:

```bash
ARATIRI_SMOKE_BASE_URL=http://127.0.0.1:2100 \
  validation/regtest/scripts/smoke-aratiri.sh
```

Results remain under `validation/regtest/runtime/results/`; login tokens remain under the mode-`0700` runtime secrets directory.

## Stop

```bash
docker compose \
  --env-file validation/regtest/runtime/compose.env \
  -f validation/regtest/compose.yml down
```

Use `down` without `-v`: the lab uses bind-mounted runtime paths and has no named volumes. Removing `runtime/` destroys the test wallets, local funds, credentials, and evidence.
