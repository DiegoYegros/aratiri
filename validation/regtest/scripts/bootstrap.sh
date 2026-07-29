#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_command docker
require_command jq
require_command od
"${SCRIPT_DIR}/prepare.sh" >/dev/null
load_lab_environment

note "Starting the pinned Bitcoin Core + two-LND regtest lab..."
dc up -d bitcoin lnd-alice lnd-bob

retry "Bitcoin Core RPC readiness" 90 2 bitcli getblockchaininfo
retry "Alice wallet creation/unlock" 90 2 lncli lnd-alice getinfo
retry "Bob wallet creation/unlock" 90 2 lncli lnd-bob getinfo

if ! bitcli -rpcwallet=miner getwalletinfo >/dev/null 2>&1; then
  if bitcli listwalletdir | jq -e '.wallets[]? | select(.name == "miner")' >/dev/null; then
    bitcli loadwallet miner >/dev/null
  else
    bitcli createwallet miner >/dev/null
  fi
fi

height="$(bitcli getblockcount)"
if ((height < 101)); then
  mine_blocks "$((101 - height))"
fi

is_synced() {
  local service="$1"
  lncli "${service}" getinfo | jq -e '.synced_to_chain == true'
}
retry "Alice chain sync before funding" 90 1 is_synced lnd-alice
retry "Bob chain sync before funding" 90 1 is_synced lnd-bob

FUNDING_BROADCAST=0
FUNDING_CONFIRMATION_NEEDED=0
FUNDING_WAIT_ALICE=0
FUNDING_WAIT_BOB=0

record_funding_state() {
  local state_file="$1"
  local service="$2"
  local identity_pubkey="$3"
  local address="$4"
  local txid="$5"
  local status="$6"

  jq -n \
    --arg service "${service}" \
    --arg identity_pubkey "${identity_pubkey}" \
    --arg address "${address}" \
    --arg txid "${txid}" \
    --arg status "${status}" \
    '{
      service: $service,
      identity_pubkey: $identity_pubkey,
      address: $address,
      txid: $txid,
      status: $status
    }' \
    >"${state_file}.tmp"
  mv "${state_file}.tmp" "${state_file}"
}

archive_funding_state() {
  local state_file="$1"
  local reason="$2"
  local archive_file="${state_file}.${reason}.json"
  local sequence=1

  while [[ -e "${archive_file}" ]]; do
    archive_file="${state_file}.${reason}.${sequence}.json"
    ((sequence += 1))
  done
  mv "${state_file}" "${archive_file}"
  note "Archived ${state_file} (${reason}); starting a fresh funding evaluation."
}

recover_funding_txid() {
  local address="$1"
  bitcli -rpcwallet=miner listtransactions "*" 1000 0 true \
    | jq -r --arg address "${address}" \
      '[.[] | select(.category == "send" and .address == $address)] | last | .txid // empty'
}

mark_funding_wait() {
  local service="$1"
  if [[ "${service}" == "lnd-alice" ]]; then
    FUNDING_WAIT_ALICE=1
  else
    FUNDING_WAIT_BOB=1
  fi
}

fund_node_if_needed() {
  local service="$1"
  local state_file="${RUNTIME_DIR}/results/${service}-funding.json"
  local wallet_json
  local total_balance
  local confirmed_balance
  local unconfirmed_balance
  local identity_pubkey
  local journal_identity
  local address
  local txid=""
  local confirmations=""

  identity_pubkey="$(lncli "${service}" getinfo | jq -r '.identity_pubkey // empty')"
  [[ -n "${identity_pubkey}" ]] || fail "Could not determine wallet identity for ${service}"
  wallet_json="$(lncli "${service}" walletbalance)"
  total_balance="$(jq -r '.total_balance | tonumber' <<<"${wallet_json}")"
  confirmed_balance="$(jq -r '.confirmed_balance | tonumber' <<<"${wallet_json}")"
  unconfirmed_balance="$(jq -r '.unconfirmed_balance | tonumber' <<<"${wallet_json}")"

  if [[ -s "${state_file}" ]]; then
    journal_identity="$(jq -r '.identity_pubkey // empty' "${state_file}")"
    if [[ -z "${journal_identity}" ]]; then
      archive_funding_state "${state_file}" "missing-wallet-identity"
    elif [[ "${journal_identity}" != "${identity_pubkey}" ]]; then
      archive_funding_state "${state_file}" "wallet-identity-mismatch"
    else
      address="$(jq -r '.address // empty' "${state_file}")"
      txid="$(jq -r '.txid // empty' "${state_file}")"
      [[ -n "${address}" ]] || fail "Invalid funding state for ${service}: missing address"
      if [[ -z "${txid}" ]]; then
        txid="$(recover_funding_txid "${address}")"
        if [[ -n "${txid}" ]]; then
          record_funding_state \
            "${state_file}" "${service}" "${identity_pubkey}" "${address}" "${txid}" "broadcast"
        fi
      fi
      if [[ -n "${txid}" ]]; then
        confirmations="$(bitcli -rpcwallet=miner gettransaction "${txid}" | jq -r '.confirmations | tonumber')"
        if ((confirmations < 1)); then
          FUNDING_CONFIRMATION_NEEDED=1
          mark_funding_wait "${service}"
          return
        fi
        record_funding_state \
          "${state_file}" "${service}" "${identity_pubkey}" "${address}" "${txid}" "confirmed"
        if ((total_balance >= 3000000)); then
          if ((confirmed_balance < 3000000 && unconfirmed_balance > 0)); then
            FUNDING_CONFIRMATION_NEEDED=1
            mark_funding_wait "${service}"
          fi
          return
        fi
        archive_funding_state "${state_file}" "confirmed-balance-depleted"
      fi
    fi
  fi

  if ((total_balance >= 3000000)); then
    if ((confirmed_balance < 3000000 && unconfirmed_balance > 0)); then
      FUNDING_CONFIRMATION_NEEDED=1
      mark_funding_wait "${service}"
    fi
    return
  fi

  if [[ ! -s "${state_file}" ]]; then
    address="$(lncli "${service}" newaddress p2wkh | jq -r '.address')"
    [[ -n "${address}" && "${address}" != "null" ]] \
      || fail "Could not obtain a funding address for ${service}"
    record_funding_state \
      "${state_file}" "${service}" "${identity_pubkey}" "${address}" "" "prepared"
  else
    address="$(jq -r '.address' "${state_file}")"
  fi
  [[ -n "${address}" && "${address}" != "null" ]] || fail "Could not obtain a funding address for ${service}"
  txid="$(bitcli -rpcwallet=miner sendtoaddress "${address}" 0.05)"
  record_funding_state \
    "${state_file}" "${service}" "${identity_pubkey}" "${address}" "${txid}" "broadcast"
  FUNDING_BROADCAST=1
  FUNDING_CONFIRMATION_NEEDED=1
  mark_funding_wait "${service}"
}

fund_node_if_needed lnd-alice
fund_node_if_needed lnd-bob
if ((FUNDING_BROADCAST == 1 || FUNDING_CONFIRMATION_NEEDED == 1)); then
  mine_blocks 6
fi

retry "Alice chain sync" 90 1 is_synced lnd-alice
retry "Bob chain sync" 90 1 is_synced lnd-bob

funding_available() {
  local service="$1"
  lncli "${service}" walletbalance \
    | jq -e '(.confirmed_balance | tonumber) >= 3000000'
}
if ((FUNDING_WAIT_ALICE == 1)); then
  retry "Alice funding confirmation" 90 1 funding_available lnd-alice
fi
if ((FUNDING_WAIT_BOB == 1)); then
  retry "Bob funding confirmation" 90 1 funding_available lnd-bob
fi

bob_pubkey="$(lncli lnd-bob getinfo | jq -r '.identity_pubkey')"
[[ -n "${bob_pubkey}" && "${bob_pubkey}" != "null" ]] || fail "Bob identity pubkey is unavailable"

if ! lncli lnd-alice listpeers | jq -e --arg pubkey "${bob_pubkey}" \
  '.peers[]? | select(.pub_key == $pubkey)' >/dev/null; then
  lncli lnd-alice connect "${bob_pubkey}@lnd-bob:9735" >/dev/null
fi

channel_active() {
  lncli lnd-alice listchannels | jq -e --arg pubkey "${bob_pubkey}" \
    '.channels[]? | select(.remote_pubkey == $pubkey and .active == true)'
}

channel_exists() {
  lncli lnd-alice listchannels | jq -e --arg pubkey "${bob_pubkey}" \
    '.channels[]? | select(.remote_pubkey == $pubkey)'
}

channel_opening() {
  lncli lnd-alice pendingchannels | jq -e --arg pubkey "${bob_pubkey}" \
    '.pending_open_channels[]? | select(.channel.remote_node_pub == $pubkey)'
}

channel_closing() {
  lncli lnd-alice pendingchannels | jq -e --arg pubkey "${bob_pubkey}" '
    [
      (.pending_closing_channels[]?),
      (.pending_force_closing_channels[]?),
      (.waiting_close_channels[]?)
    ]
    | map(select(.channel.remote_node_pub == $pubkey))
    | length > 0
  '
}

if channel_closing >/dev/null 2>&1; then
  fail "Alice and Bob have a closing channel; wait for it to close before bootstrapping a replacement"
elif channel_exists >/dev/null 2>&1; then
  note "Alice and Bob already have a channel; waiting for it to become active."
elif channel_opening >/dev/null 2>&1; then
  note "Alice and Bob already have a pending-open channel; mining confirmations."
  mine_blocks 6
else
  lncli lnd-alice openchannel \
    --sat_per_vbyte=1 \
    "${bob_pubkey}" \
    2000000 \
    750000 >"${RUNTIME_DIR}/results/bootstrap-open-channel.json"
  mine_blocks 6
fi
retry "active Alice-Bob channel" 90 1 channel_active

export_dir="${RUNTIME_DIR}/export"
umask 077
dc exec -T lnd-alice cat /root/.lnd/tls.cert >"${export_dir}/tls.cert"
dc exec -T lnd-alice cat /root/.lnd/data/chain/bitcoin/regtest/admin.macaroon \
  | od -An -v -tx1 \
  | tr -d '[:space:]' >"${export_dir}/admin.macaroon.hex"
chmod 600 "${export_dir}/tls.cert" "${export_dir}/admin.macaroon.hex"

cat >"${export_dir}/aratiri.env" <<EOF
# Generated regtest-only Aratiri LND client settings.
# Merge these with the normal local DB/Kafka/JWT environment before bootRun.
GRPC_CLIENT_LND_NAME=127.0.0.1
GRPC_CLIENT_LND_PORT=${ALICE_GRPC_PORT}
GRPC_TLS_ACTIVE=true
LND_TLS_CERT_PATH=${export_dir}/tls.cert
ADMIN_MACAROON_PATH=${export_dir}/admin.macaroon.hex
EOF
chmod 600 "${export_dir}/aratiri.env"

cat >"${RUNTIME_DIR}/EVIDENCE_STATUS.txt" <<'EOF'
PROVISIONAL REGTEST EVIDENCE

These test-only results are not production or public-testnet evidence.
Acceptance is provisional pending tutor approval of D-05.
No real funds or personal data are used by this lab.
EOF

note "Regtest lab is ready."
note "Aratiri LND settings: ${export_dir}/aratiri.env"
note "Evidence status: PROVISIONAL pending D-05 tutor approval."
