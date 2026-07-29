#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_command docker
require_command jq
load_lab_environment

results_dir="${RUNTIME_DIR}/results/direct"
mkdir -p "${results_dir}"
chmod 700 "${results_dir}"

retry "active regtest nodes" 5 1 lncli lnd-alice getinfo

note "Checking a direct Lightning terminal settlement..."
invoice_json="$(lncli lnd-alice addinvoice --amt=10000 --memo=aratiri-direct-smoke)"
invoice="$(jq -r '.payment_request' <<<"${invoice_json}")"
payment_hash="$(lncli lnd-alice decodepayreq "${invoice}" | jq -r '.payment_hash')"
[[ -n "${invoice}" && "${invoice}" != "null" ]] || fail "Alice did not return a payment request"

lncli lnd-bob payinvoice \
  --force \
  --fee_limit=100 \
  --timeout=30 \
  "${invoice}" >"${results_dir}/lightning-payment.json"
jq -e '.status == "SUCCEEDED"' "${results_dir}/lightning-payment.json" >/dev/null \
  || fail "Direct Lightning payment did not reach SUCCEEDED"

invoice_settled() {
  lncli lnd-alice lookupinvoice "${payment_hash}" | jq -e '.state == "SETTLED"'
}
retry "Alice invoice SETTLED state" 30 1 invoice_settled
lncli lnd-alice lookupinvoice "${payment_hash}" >"${results_dir}/lightning-invoice.json"

if lncli lnd-bob payinvoice \
  --force \
  --fee_limit=100 \
  --timeout=10 \
  "${invoice}" >"${results_dir}/lightning-duplicate.txt" 2>&1; then
  fail "LND unexpectedly accepted a duplicate terminal invoice payment"
fi

note "Checking a direct on-chain terminal settlement..."
bob_address="$(lncli lnd-bob newaddress p2wkh | jq -r '.address')"
bob_balance_before="$(lncli lnd-bob walletbalance | jq -r '.confirmed_balance | tonumber')"
send_json="$(lncli lnd-alice sendcoins --sat_per_vbyte=1 "${bob_address}" 50000)"
txid="$(jq -r '.txid' <<<"${send_json}")"
[[ -n "${txid}" && "${txid}" != "null" ]] || fail "Alice did not return an on-chain txid"
mine_blocks 6

onchain_settled() {
  lncli lnd-bob listchaintxns | jq -e --arg txid "${txid}" \
    '.transactions[]? | select(.tx_hash == $txid and (.num_confirmations | tonumber) >= 1)'
}
retry "Bob on-chain confirmation" 60 1 onchain_settled
bob_balance_after="$(lncli lnd-bob walletbalance | jq -r '.confirmed_balance | tonumber')"
((bob_balance_after - bob_balance_before == 50000)) \
  || fail "Bob confirmed balance changed by $((bob_balance_after - bob_balance_before)) sats, expected 50000"

lncli lnd-bob listchaintxns >"${results_dir}/onchain-transactions.json"
jq -n \
  --arg payment_hash "${payment_hash}" \
  --arg txid "${txid}" \
  --arg evidence "PROVISIONAL pending D-05 tutor approval" \
  '{lightning_payment_hash: $payment_hash, onchain_txid: $txid, evidence_status: $evidence}' \
  >"${results_dir}/summary.json"

note "Direct Lightning and on-chain smoke checks passed."
note "Evidence is PROVISIONAL pending D-05 tutor approval."
