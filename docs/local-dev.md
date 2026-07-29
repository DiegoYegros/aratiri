# Local Dev Stack

Quick start for running Aratiri on this machine without a full Bitcoin Core node.

## What's running

| Service | How | Port |
| --- | --- | --- |
| PostgreSQL | Docker `postgres_db` | `5432` |
| Kafka | Docker `kafka` | `9092` |
| LND (neutrino, **testnet**) | Docker `aratiri_lnd` | gRPC `10009`, REST `8080` |
| Aratiri API | `./gradlew bootRun` | `2100` |

LND uses **neutrino** peers (no local `bitcoind`). It will take a while to sync headers; the API can still start and talk to LND while syncing.

## Start / stop

```bash
cd ~/repos/aratiri

# infra
docker compose --env-file .env -f docker-compose.yml -f docker-compose.lnd.yml \
  --profile db-only --profile kafka-only --profile lnd up -d db kafka lnd

# app (loads .env from shell)
set -a && source .env && set +a
./gradlew bootRun
```

Secrets (gitignored): `secrets/admin.macaroon`, `secrets/tls.cert`, `secrets/lnd-testnet-seed.txt`.

If LND restarts and asks for unlock:

```bash
docker exec -it aratiri_lnd lncli --network=testnet unlock
# password: see secrets/lnd-testnet-seed.txt
```

## Useful URLs

- API: http://localhost:2100
- Swagger: http://localhost:2100/swagger-ui/index.html
- OpenAPI: http://localhost:2100/v3/api-docs

## Notes

- `.env` is configured for **host** `bootRun` (`localhost` DB/Kafka/LND), not for the dockerized `aratiri-app` service.
- LND network is **testnet** (matches TFG Testnet plan).
- `/v1/auth/register` tries to send verification email via SMTP. Until real `EMAIL_*` credentials are set, registration fails with `MailAuthenticationException`. API/Swagger and LND gRPC still work.
- Logs: `/tmp/aratiri-bootrun.log` when started with the nohup pattern above.
