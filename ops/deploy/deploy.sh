#!/usr/bin/env bash
# Aratiri deploy poller.
#
# Runs on a schedule (systemd timer, see aratiri-deploy.timer). Compares the
# deployed image digest against the GHCR :latest digest for each Aratiri image
# and runs `docker compose up -d` only when something changed.
#
# flock-guarded so concurrent timer/retry runs never race.
# Logs to stdout/stderr; systemd journal captures it (journal-friendly).
#
# The compose directory is resolved from this script's location by default, so
# the script works wherever ops/ is dropped (e.g. ~/aratiri-deploy/ops/):
#   <compose-dir>/ops/deploy/deploy.sh  ->  <compose-dir>
#
# Usage:
#   deploy.sh [--dry-run] [compose-file]   # default: <compose-dir>/docker-compose.yml
#
#   --dry-run   report what a real run would do without pulling, logging in, or
#               touching running containers. Used for staging/validation.
#
# Environment (also read from the systemd EnvironmentFile):
#   ARATIRI_COMPOSE_DIR   compose + .env directory (override auto-detection)
#   ARATIRI_DEPLOY_HOLD   if set to 1, exit 0 without pulling (timer-safe pause)
#   GHCR_USERNAME         GHCR login username (optional if already logged in)
#   GHCR_TOKEN            GHCR fine-grained PAT (optional if already logged in)
#
# Hold file (also pauses without env):
#   <compose-dir>/.deploy.hold   presence alone skips pull/redeploy (exit 0)

set -euo pipefail

DRY_RUN=0
COMPOSE_FILE_ARG=""
for arg in "$@"; do
  case "${arg}" in
    --dry-run) DRY_RUN=1 ;;
    -*) echo "unknown option: ${arg}" >&2; exit 1 ;;
    *) COMPOSE_FILE_ARG="${arg}" ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_image_refs.sh
source "${SCRIPT_DIR}/lib_image_refs.sh"
# shellcheck source=lib_deploy_hold.sh
source "${SCRIPT_DIR}/lib_deploy_hold.sh"
# Script lives at <compose-dir>/ops/deploy/deploy.sh, so climb two levels.
COMPOSE_DIR="${ARATIRI_COMPOSE_DIR:-$(dirname "$(dirname "${SCRIPT_DIR}")")}"
COMPOSE_FILE="${COMPOSE_FILE_ARG:-${COMPOSE_DIR}/docker-compose.yml}"
ENV_FILE="${COMPOSE_DIR}/.env"
# Writable by the user the timer runs as (daya). Kept out of /run since that
# is root-owned and the poller deliberately runs unprivileged.
LOCK_FILE="${COMPOSE_DIR}/.deploy.lock"
LOG_TAG="aratiri-deploy"

# Image refs whose digest change triggers a redeploy. Must match the qualified
# GHCR names asserted from the active compose file (see lib_image_refs.sh).
IMAGES=(
  "ghcr.io/diegoyegros/aratiri:latest"
  "ghcr.io/diegoyegros/aratiri-frontend:latest"
  "ghcr.io/diegoyegros/aratiri-admin:latest"
)

log() { echo "$(date -Is) [$LOG_TAG] $*"; }

# Pause pull/redeploy so the timer can stay enabled while GHCR catches up.
if is_deploy_hold_active "${COMPOSE_DIR}"; then
  log "deploy hold active; skipping"
  exit 0
fi

# Digest of the image the running container was created from.
deployed_digest() {
  local container="$1"
  docker inspect -f '{{.Image}}' "${container}" 2>/dev/null || true
}

# Digest of the local copy of an image ref (empty if not present).
local_digest() {
  docker image inspect -f '{{.Id}}' "$1" 2>/dev/null || true
}

compose_cmd() {
  docker compose --project-directory "${COMPOSE_DIR}" \
    -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

redeploy() {
  log "image changed; pulling and redeploying"
  compose_cmd pull
  compose_cmd up -d
  log "redeploy complete"
}

# Serialize concurrent runs.
exec 9>"${LOCK_FILE}"
flock -n 9 || { log "another deploy run in progress; skipping"; exit 0; }

# Read GHCR credentials from the env file without sourcing it wholesale
# (values may contain characters that trip `set -e` parsing).
read_env() {
  local key="$1" value=""
  if [ -f "${ENV_FILE}" ]; then
    value="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n1 | cut -d= -f2- || true)"
  fi
  printf '%s' "${value}"
}

GHCR_USERNAME="${GHCR_USERNAME:-$(read_env GHCR_USERNAME)}"
GHCR_TOKEN="${GHCR_TOKEN:-$(read_env GHCR_TOKEN)}"

if [ "${DRY_RUN}" -eq 0 ] && [ -n "${GHCR_USERNAME}" ] && [ -n "${GHCR_TOKEN}" ]; then
  echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin >/dev/null 2>&1 \
    && log "logged in to GHCR" || log "GHCR login failed; relying on cached credentials"
fi

if [ ! -f "${COMPOSE_FILE}" ]; then
  log "compose file not found: ${COMPOSE_FILE}"
  exit 1
fi

# Fail-closed before pull/redeploy: local/unqualified tags never soft-fail.
# Soft-fail on compose pull remains only for valid GHCR refs (network/auth).
if ! assert_out="$(assert_compose_aratiri_ghcr_images "${COMPOSE_FILE}")"; then
  while IFS= read -r line; do
    [ -n "${line}" ] && log "${line}"
  done <<< "${assert_out}"
  log "refusing pull/redeploy until compose uses qualified GHCR image refs"
  exit 1
fi

if [ "${DRY_RUN}" -eq 1 ]; then
  log "dry-run: comparing deployed digests against local ${COMPOSE_FILE} (no pull, no up)"
else
  # First make the local :latest tags current. This is a registry manifest
  # check (no layer downloads) when nothing changed; image IDs stay identical.
  log "refreshing image tags"
  if ! compose_cmd pull; then
    # e.g. GHCR package still private / network down. Compare against whatever
    # local images exist and retry next cycle rather than failing the unit.
    log "compose pull failed; continuing with local images"
  fi
fi

changed=0
for image in "${IMAGES[@]}"; do
  # Container name derived from the compose service (see docker-compose.prod.yml).
  # Backend runs as "aratiri-backend"; frontend as "aratiri-frontend";
  # admin as "aratiri-admin".
  container=""
  case "${image}" in
    *"/aratiri:latest") container="aratiri-backend" ;;
    *"/aratiri-frontend:latest") container="aratiri-frontend" ;;
    *"/aratiri-admin:latest") container="aratiri-admin" ;;
  esac
  if [ -z "${container}" ]; then
    log "no container mapping for ${image}; skipping"
    continue
  fi

  deployed="$(deployed_digest "${container}")"
  local="$(local_digest "${image}")"

  if [ -z "${deployed}" ]; then
    log "${container} not running; scheduled for (re)deploy"
    changed=1
  elif [ -z "${local}" ] || [ "${deployed}" != "${local}" ]; then
    log "${container} digest differs (deployed=${deployed} local=${local:-none})"
    changed=1
  fi
done

if [ "${changed}" -eq 1 ]; then
  if [ "${DRY_RUN}" -eq 1 ]; then
    log "dry-run: image change detected, would run 'compose pull && up -d'"
    exit 0
  fi
  redeploy
else
  log "no image changes; nothing to do"
fi
