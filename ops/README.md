# ops/ — Production deployment tooling

Push-to-master deployment for the Aratiri stack. Heavy builds run on GitHub
Actions, which pushes Docker images to GHCR. The server never builds; it
polls GHCR and redeploys on image change.

## How it works

1. `.github/workflows/deploy.yml` in each repo (`aratiri`, `aratiri-frontend`,
   `aratiri-admin-frontend`) — on push to `master`/`main`, run the existing
   build check, then build and push `ghcr.io/diegoyegros/{aratiri,aratiri-frontend,aratiri-admin}`
   tagged `latest` and `${{ github.sha }}`. Uses the built-in `GITHUB_TOKEN`,
   no extra secrets.
2. Server systemd timer runs `deploy/deploy.sh` every 3 minutes. It compares
   the running container's image digest to the local `:latest` digest; on
   change it runs `docker compose pull && up -d`.

## Server layout (convert of the existing ~/aratiri-deploy)

The live stack already exists at `~/aratiri-deploy` (postgres + kafka + lnd +
backend + frontend + admin). It is NOT redesigned — only the three `build:`
sections are replaced by GHCR image refs (see `docker-compose.prod.yml`,
which is that converted file). Preserve the exposure exactly: app HTTP binds
`127.0.0.1:{2100,3100,3101}`, LND p2p `:9735`, kafka/db have no host ports.

```
~/aratiri-deploy/
  docker-compose.yml          # converted: GHCR image refs (from docker-compose.prod.yml)
  .env                        # real secrets (keys mirror ops/.env.example)
  secrets/admin.macaroon      # LND admin macaroon (mount point)
  secrets/tls.cert            # LND TLS cert (mount point)
  lnd-data/                   # LND data volume bind
  ops/deploy/deploy.sh        # poller (copied from this repo)
```

## Convert to GHCR images (one-time, on the server)

In `~/aratiri-deploy/docker-compose.yml`, replace each `build:` block with the
matching image ref:

- `aratiri-app`       -> `image: ghcr.io/diegoyegros/aratiri:latest`
- `aratiri-frontend`  -> `image: ghcr.io/diegoyegros/aratiri-frontend:latest`
- `aratiri-admin`     -> `image: ghcr.io/diegoyegros/aratiri-admin:latest`

Keep every container name, port, env var, healthcheck and the memory tuning
(`KAFKA_HEAP_OPTS=-Xms256m -Xmx256m`, `JAVA_TOOL_OPTIONS=-Xmx384m`,
`HIKARI_MAXIMUM_POOL_SIZE=10`) untouched.

## Install (server, once)

```bash
# copy the poller
mkdir -p ~/aratiri-deploy/ops
cp -r ops/deploy ~/aratiri-deploy/ops/

# install units (adjust User= to the compose owner)
sudo cp ops/deploy/aratiri-deploy.service /etc/systemd/system/
sudo cp ops/deploy/aratiri-deploy.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now aratiri-deploy.timer
```

First deploy manually:

```bash
cd ~/aratiri-deploy
docker compose pull
docker compose up -d
```

## GHCR pull authentication

The repos are PUBLIC on GitHub, but GHCR packages default to PRIVATE until
flipped in each package's settings. Until then the server must authenticate:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USERNAME" --password-stdin
```

`GHCR_USERNAME` / `GHCR_TOKEN` (fine-grained PAT, `read:packages` on the three
repos) go in `~/aratiri-deploy/.env`. `deploy.sh` uses them when present; once
packages are public, anonymous pulls work and the login is a no-op.

## Memory budget (2 GB server, preserved from the tuned deploy)

- Kafka: `KAFKA_HEAP_OPTS=-Xms256m -Xmx256m`
- Backend: `JAVA_TOOL_OPTIONS=-Xmx384m`, hikari pool 10
- Frontend/Admin: Next standalone, no tuning beyond `PORT`

## Note on NEXT_PUBLIC_API_BASE_URL

Next.js inlines `NEXT_PUBLIC_*` at build time. The frontend and admin images
are built by CI with the `NEXT_PUBLIC_API_BASE_URL` build arg
(`http://167.233.193.136:2100/v1`; the `/v1` suffix is required by the API
client). The value in `ops/.env.example` is for reference only — changing it
at runtime has no effect on the built image.
