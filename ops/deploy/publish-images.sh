#!/usr/bin/env bash
# Tag local Aratiri images and push them to GHCR as :latest.
#
# Use when CI has not yet published current Batch digests, or to backfill
# GHCR before re-enabling aratiri-deploy.timer.
#
# Usage:
#   publish-images.sh <aratiri-local-ref> <frontend-local-ref> <admin-local-ref>
#
# Example:
#   publish-images.sh aratiri:batch4 aratiri-frontend:batch4 aratiri-admin:batch4
#
# Environment:
#   GHCR_USERNAME   GHCR login username (optional if already logged in)
#   GHCR_TOKEN      PAT with write:packages (optional if already logged in)
#   GHCR_OWNER      package owner (default: diegoyegros)

set -euo pipefail

OWNER="${GHCR_OWNER:-diegoyegros}"
REGISTRY="ghcr.io/${OWNER}"

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <aratiri-local-ref> <frontend-local-ref> <admin-local-ref>

Tags each local image as ${REGISTRY}/{aratiri,aratiri-frontend,aratiri-admin}:latest
and pushes to GHCR. Provide GHCR_USERNAME/GHCR_TOKEN (PAT with write:packages)
if docker is not already logged in with push rights.
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "$#" -ne 3 ]; then
  usage
  exit 1
fi

LOCAL_ARATIRI="$1"
LOCAL_FRONTEND="$2"
LOCAL_ADMIN="$3"

log() { echo "$(date -Is) [aratiri-publish] $*"; }

fail_permission() {
  local detail="$1"
  cat >&2 <<EOF
GHCR push failed (permission denied).
${detail}

A PAT (or token) with write:packages is required, and the account must have
write access to ${REGISTRY}/{aratiri,aratiri-frontend,aratiri-admin}.
Set GHCR_USERNAME / GHCR_TOKEN and retry, or log in manually:
  echo "\$GHCR_TOKEN" | docker login ghcr.io -u "\$GHCR_USERNAME" --password-stdin
EOF
  exit 1
}

is_permission_error() {
  local text="$1"
  echo "${text}" | grep -qiE \
    'permission_denied|denied|unauthorized|authentication required|insufficient_scope'
}

publish_one() {
  local pkg="$1"
  local local_ref="$2"
  local remote="${REGISTRY}/${pkg}:latest"

  if ! docker image inspect "${local_ref}" >/dev/null 2>&1; then
    echo "local image not found: ${local_ref}" >&2
    exit 1
  fi

  log "tagging ${local_ref} -> ${remote}"
  docker tag "${local_ref}" "${remote}"

  log "pushing ${remote}"
  set +e
  push_out="$(docker push "${remote}" 2>&1)"
  push_rc=$?
  set -e
  echo "${push_out}"
  if [ "${push_rc}" -ne 0 ]; then
    if is_permission_error "${push_out}"; then
      fail_permission "docker push ${remote}"
    fi
    echo "docker push failed for ${remote} (exit ${push_rc})" >&2
    exit "${push_rc}"
  fi
  log "pushed ${remote}"
}

if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  log "logging in to ghcr.io as ${GHCR_USERNAME}"
  if ! login_out="$(echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USERNAME}" --password-stdin 2>&1)"; then
    if is_permission_error "${login_out}"; then
      fail_permission "docker login: ${login_out}"
    fi
    echo "docker login failed: ${login_out}" >&2
    exit 1
  fi
  log "logged in to GHCR"
elif [ -z "${GHCR_USERNAME:-}" ] && [ -z "${GHCR_TOKEN:-}" ]; then
  log "GHCR_USERNAME/GHCR_TOKEN unset; using existing docker credentials"
elif [ -z "${GHCR_USERNAME:-}" ] || [ -z "${GHCR_TOKEN:-}" ]; then
  echo "both GHCR_USERNAME and GHCR_TOKEN must be set together (or neither)" >&2
  exit 1
fi

publish_one aratiri "${LOCAL_ARATIRI}"
publish_one aratiri-frontend "${LOCAL_FRONTEND}"
publish_one aratiri-admin "${LOCAL_ADMIN}"

log "all three images published to ${REGISTRY}"
