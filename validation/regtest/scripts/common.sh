#!/usr/bin/env bash

set -euo pipefail

REGTEST_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${REGTEST_DIR}/runtime"
COMPOSE_FILE="${REGTEST_DIR}/compose.yml"
COMPOSE_ENV="${RUNTIME_DIR}/compose.env"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

note() {
  printf '%s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

load_lab_environment() {
  [[ -s "${COMPOSE_ENV}" ]] || fail "Missing ${COMPOSE_ENV}; run validation/regtest/scripts/prepare.sh"
  set -a
  # This file is generated locally with mode 0600 and is never committed.
  # shellcheck disable=SC1090
  source "${COMPOSE_ENV}"
  set +a
}

dc() {
  docker compose --env-file "${COMPOSE_ENV}" -f "${COMPOSE_FILE}" "$@"
}

bitcli() {
  dc exec -T bitcoin bitcoin-cli \
    -regtest \
    -rpcconnect=127.0.0.1 \
    -rpcuser="${BITCOIN_RPC_USER}" \
    -rpcpassword="${BITCOIN_RPC_PASSWORD}" \
    "$@"
}

lncli() {
  local service="$1"
  shift
  dc exec -T "${service}" lncli --network=regtest "$@"
}

retry() {
  local description="$1"
  local attempts="$2"
  local delay_seconds="$3"
  shift 3

  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${delay_seconds}"
  done
  fail "Timed out waiting for ${description}"
}

mine_blocks() {
  local count="$1"
  local mining_address
  mining_address="$(bitcli -rpcwallet=miner getnewaddress)"
  bitcli -rpcwallet=miner generatetoaddress "${count}" "${mining_address}" >/dev/null
}

transaction_status() {
  local base_url="$1"
  local token="$2"
  local transaction_id="$3"
  local output_file="$4"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${base_url}/v1/transactions/${transaction_id}" \
    -o "${output_file}"
  jq -r '.status // empty' "${output_file}"
}
