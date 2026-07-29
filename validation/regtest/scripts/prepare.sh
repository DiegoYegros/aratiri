#!/usr/bin/env bash

set -euo pipefail

REGTEST_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${REGTEST_DIR}/runtime"
COMPOSE_ENV="${RUNTIME_DIR}/compose.env"

command -v od >/dev/null 2>&1 || {
  printf 'ERROR: required command not found: od\n' >&2
  exit 1
}

umask 077
mkdir -p \
  "${RUNTIME_DIR}/bitcoin" \
  "${RUNTIME_DIR}/lnd-alice" \
  "${RUNTIME_DIR}/lnd-bob" \
  "${RUNTIME_DIR}/export" \
  "${RUNTIME_DIR}/results" \
  "${RUNTIME_DIR}/secrets"
chmod 700 "${RUNTIME_DIR}" "${RUNTIME_DIR}"/*

if [[ ! -s "${COMPOSE_ENV}" ]]; then
  rpc_password="$(od -An -N32 -tx1 /dev/urandom | tr -d '[:space:]')"
  {
    printf 'BITCOIN_RPC_USER=aratiri_regtest\n'
    printf 'BITCOIN_RPC_PASSWORD=%s\n' "${rpc_password}"
    printf 'ALICE_GRPC_PORT=11009\n'
    printf 'ALICE_REST_PORT=18081\n'
    printf 'BOB_GRPC_PORT=12009\n'
    printf 'BOB_REST_PORT=18082\n'
  } >"${COMPOSE_ENV}"
fi
chmod 600 "${COMPOSE_ENV}"

printf 'Prepared gitignored runtime directory: %s\n' "${RUNTIME_DIR}"
