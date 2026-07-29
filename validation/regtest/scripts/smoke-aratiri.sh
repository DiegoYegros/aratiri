#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command docker
require_command jq
load_lab_environment
umask 077

BASE_URL="${ARATIRI_SMOKE_BASE_URL:-http://127.0.0.1:2100}"
ALICE_EMAIL="${ARATIRI_SMOKE_ALICE_EMAIL:-alice@example.com}"
BOB_EMAIL="${ARATIRI_SMOKE_BOB_EMAIL:-bob@example.com}"
LOCAL_PASSWORD="${ARATIRI_SMOKE_LOCAL_PASSWORD:-password123}"
results_dir="${RUNTIME_DIR}/results/aratiri"
secrets_dir="${RUNTIME_DIR}/secrets"
mkdir -p "${results_dir}" "${secrets_dir}"
chmod 700 "${results_dir}" "${secrets_dir}"

prerequisite_error() {
  fail "$*. Start the normal PostgreSQL/Kafka stack, run bootstrap.sh, merge runtime/export/aratiri.env into the app environment, restart Aratiri, and ensure Flyway seeded alice@example.com and bob@example.com. This runner will not report partial HTTP acceptance as success."
}

http_json() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="$4"
  local idempotency_key="$5"
  local output_file="$6"
  local expected_status="$7"
  local -a args=(
    --silent
    --show-error
    --request "${method}"
    --header "Accept: application/json"
    --output "${output_file}"
    --write-out "%{http_code}"
  )
  if [[ -n "${token}" ]]; then
    args+=(--header "Authorization: Bearer ${token}")
  fi
  if [[ -n "${body}" ]]; then
    args+=(--header "Content-Type: application/json" --data "${body}")
  fi
  if [[ -n "${idempotency_key}" ]]; then
    args+=(--header "Idempotency-Key: ${idempotency_key}")
  fi

  local status
  status="$(curl "${args[@]}" "${BASE_URL}${path}")" \
    || prerequisite_error "HTTP request failed for ${method} ${path}"
  [[ "${status}" == "${expected_status}" ]] \
    || prerequisite_error "${method} ${path} returned HTTP ${status}, expected ${expected_status}; response is in ${output_file}"
}

login() {
  local email="$1"
  local output_file="$2"
  local login_body
  login_body="$(jq -nc --arg username "${email}" --arg password "${LOCAL_PASSWORD}" \
    '{username: $username, password: $password}')"
  http_json POST /v1/auth/login "" "${login_body}" "" "${output_file}" 200
  jq -er '.accessToken' "${output_file}" \
    || prerequisite_error "Seeded login failed for ${email}"
}

[[ -s "${RUNTIME_DIR}/export/admin.macaroon.hex" ]] \
  || prerequisite_error "Missing exported Alice macaroon"
[[ -s "${RUNTIME_DIR}/export/tls.cert" ]] \
  || prerequisite_error "Missing exported Alice TLS certificate"
retry "Alice LND readiness" 5 1 lncli lnd-alice getinfo
retry "Bob LND readiness" 5 1 lncli lnd-bob getinfo

alice_token="$(login "${ALICE_EMAIL}" "${secrets_dir}/alice-login.json")"
bob_token="$(login "${BOB_EMAIL}" "${secrets_dir}/bob-login.json")"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${bob_token}" \
  "${BASE_URL}/actuator/health" >/dev/null \
  || prerequisite_error "Authenticated Aratiri health endpoint is unavailable at ${BASE_URL}"

note "Crediting seeded Bob through an Aratiri invoice on the real regtest LND..."
credit_body="$(jq -nc \
  --arg memo "aratiri-regtest-credit" \
  --arg external_reference "regtest-credit" \
  '{sats_amount: 150000, memo: $memo, external_reference: $external_reference}')"
http_json POST /v1/invoices "${bob_token}" "${credit_body}" "" \
  "${results_dir}/credit-invoice-http.json" 201
credit_invoice="$(jq -er '.payment_request' "${results_dir}/credit-invoice-http.json")"
credit_hash="$(lncli lnd-alice decodepayreq "${credit_invoice}" | jq -r '.payment_hash')"

# This proves the API is connected to the exported Alice node, not a mock or another LND.
lncli lnd-alice lookupinvoice "${credit_hash}" \
  >"${results_dir}/credit-invoice-node-before.json" \
  || prerequisite_error "Aratiri-created invoice is absent from Alice LND; restart the app with runtime/export/aratiri.env"

lncli lnd-bob payinvoice \
  --force \
  --fee_limit=100 \
  --timeout=30 \
  "${credit_invoice}" >"${results_dir}/credit-payment-node.json"
jq -e '.status == "SUCCEEDED"' "${results_dir}/credit-payment-node.json" >/dev/null \
  || prerequisite_error "Bob LND could not pay the Aratiri funding invoice"

bob_credit_completed() {
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${bob_token}" \
    "${BASE_URL}/v1/transactions?limit=100" \
    -o "${results_dir}/bob-transactions-after-credit.json" \
    && jq -e --arg hash "${credit_hash}" \
      '[.transactions[]? | select(.referenceId == $hash and .status == "COMPLETED")] | length == 1' \
      "${results_dir}/bob-transactions-after-credit.json"
}
retry "one terminal Bob credit in Aratiri" 90 1 bob_credit_completed

note "Separating Lightning HTTP acceptance from terminal settlement..."
external_invoice_json="$(lncli lnd-bob addinvoice --amt=25000 --memo=aratiri-external-smoke)"
external_invoice="$(jq -r '.payment_request' <<<"${external_invoice_json}")"
external_hash="$(lncli lnd-bob decodepayreq "${external_invoice}" | jq -r '.payment_hash')"
lightning_key="regtest-lightning-${external_hash}"
lightning_body="$(jq -nc --arg invoice "${external_invoice}" \
  '{invoice: $invoice, feeLimitSat: 100, timeoutSeconds: 30, external_reference: "regtest-lightning"}')"

http_json POST /v1/payments/invoice "${bob_token}" "${lightning_body}" "${lightning_key}" \
  "${results_dir}/lightning-accepted.json" 202
lightning_transaction_id="$(jq -er '.transactionId' "${results_dir}/lightning-accepted.json")"
jq -e '.status == "PENDING"' "${results_dir}/lightning-accepted.json" >/dev/null \
  || fail "Lightning HTTP response was not the expected PENDING acceptance"

http_json POST /v1/payments/invoice "${bob_token}" "${lightning_body}" "${lightning_key}" \
  "${results_dir}/lightning-replay-during-processing.json" 202
replay_transaction_id="$(jq -er '.transactionId' "${results_dir}/lightning-replay-during-processing.json")"
[[ "${replay_transaction_id}" == "${lightning_transaction_id}" ]] \
  || fail "Lightning idempotent replay returned a different transaction"

conflict_body="$(jq -nc --arg invoice "${external_invoice}" \
  '{invoice: $invoice, feeLimitSat: 101, timeoutSeconds: 30, external_reference: "regtest-lightning"}')"
http_json POST /v1/payments/invoice "${bob_token}" "${conflict_body}" "${lightning_key}" \
  "${results_dir}/lightning-key-conflict.json" 409

lightning_completed() {
  local status
  status="$(transaction_status "${BASE_URL}" "${bob_token}" "${lightning_transaction_id}" \
    "${results_dir}/lightning-terminal.json")"
  [[ "${status}" == "COMPLETED" ]]
}
retry "Aratiri Lightning transaction COMPLETED" 120 1 lightning_completed

lncli lnd-bob lookupinvoice "${external_hash}" >"${results_dir}/lightning-node-terminal.json"
jq -e '.state == "SETTLED" and (.amt_paid_sat | tonumber) == 25000' \
  "${results_dir}/lightning-node-terminal.json" >/dev/null \
  || fail "Aratiri reported COMPLETED but Bob LND invoice is not SETTLED"

lncli lnd-alice listpayments >"${results_dir}/alice-payments.json"
payment_effect_count="$(jq --arg hash "${external_hash}" \
  '[.payments[]? | select(.payment_hash == $hash and .status == "SUCCEEDED")] | length' \
  "${results_dir}/alice-payments.json")"
[[ "${payment_effect_count}" == "1" ]] \
  || fail "Expected exactly one successful Alice LND payment effect, found ${payment_effect_count}"

http_json POST /v1/payments/invoice "${bob_token}" "${lightning_body}" "${lightning_key}" \
  "${results_dir}/lightning-replay-after-terminal.json" 202
[[ "$(jq -er '.transactionId' "${results_dir}/lightning-replay-after-terminal.json")" == "${lightning_transaction_id}" ]] \
  || fail "Terminal Lightning replay returned a different transaction"

note "Separating on-chain HTTP acceptance from terminal broadcast settlement..."
onchain_address="$(lncli lnd-bob newaddress p2wkh | jq -r '.address')"
bob_onchain_before="$(lncli lnd-bob walletbalance | jq -r '.confirmed_balance | tonumber')"
onchain_key="regtest-onchain-${external_hash}"
onchain_body="$(jq -nc --arg address "${onchain_address}" \
  '{address: $address, sats_amount: 15000, sat_per_vbyte: 1, external_reference: "regtest-onchain"}')"

http_json POST /v1/payments/onchain "${bob_token}" "${onchain_body}" "${onchain_key}" \
  "${results_dir}/onchain-accepted.json" 202
onchain_transaction_id="$(jq -er '.transactionId' "${results_dir}/onchain-accepted.json")"
jq -e '.transactionStatus == "PENDING"' "${results_dir}/onchain-accepted.json" >/dev/null \
  || fail "On-chain HTTP response was not the expected PENDING acceptance"

http_json POST /v1/payments/onchain "${bob_token}" "${onchain_body}" "${onchain_key}" \
  "${results_dir}/onchain-replay.json" 202
[[ "$(jq -er '.transactionId' "${results_dir}/onchain-replay.json")" == "${onchain_transaction_id}" ]] \
  || fail "On-chain idempotent replay returned a different transaction"

onchain_completed() {
  local status
  status="$(transaction_status "${BASE_URL}" "${bob_token}" "${onchain_transaction_id}" \
    "${results_dir}/onchain-terminal.json")"
  [[ "${status}" == "COMPLETED" ]]
}
retry "Aratiri on-chain transaction COMPLETED after LND broadcast" 120 1 onchain_completed
mine_blocks 6

bob_received_once() {
  local balance
  balance="$(lncli lnd-bob walletbalance | jq -r '.confirmed_balance | tonumber')"
  ((balance - bob_onchain_before == 15000))
}
retry "exactly one confirmed 15000-sat Bob on-chain receipt" 60 1 bob_received_once

http_json POST /v1/payments/onchain "${bob_token}" "${onchain_body}" "${onchain_key}" \
  "${results_dir}/onchain-replay-after-terminal.json" 202
[[ "$(jq -er '.transactionId' "${results_dir}/onchain-replay-after-terminal.json")" == "${onchain_transaction_id}" ]] \
  || fail "Terminal on-chain replay returned a different transaction"

jq -n \
  --arg credit_hash "${credit_hash}" \
  --arg lightning_transaction_id "${lightning_transaction_id}" \
  --arg lightning_hash "${external_hash}" \
  --arg onchain_transaction_id "${onchain_transaction_id}" \
  --arg evidence "PROVISIONAL pending D-05 tutor approval" \
  '{
    seeded_user_credit_hash: $credit_hash,
    lightning_transaction_id: $lightning_transaction_id,
    lightning_payment_hash: $lightning_hash,
    onchain_transaction_id: $onchain_transaction_id,
    http_acceptance_checked_separately: true,
    terminal_settlement_checked: true,
    duplicate_node_effects: 0,
    evidence_status: $evidence
  }' >"${results_dir}/summary.json"

note "Aratiri real-LND Lightning and on-chain smoke checks passed."
note "Evidence is PROVISIONAL pending D-05 tutor approval."
