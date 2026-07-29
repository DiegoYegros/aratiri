#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
image="${K6_IMAGE:-grafana/k6:0.52.0}"
run_id="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="${RESULT_DIR:-$script_dir/results/$run_id}"

case "${WORKLOAD:-transactions}" in
  currencies|transactions|custom) ;;
  *)
    echo "WORKLOAD must be currencies, transactions, or custom" >&2
    exit 2
    ;;
esac

request_method="${REQUEST_METHOD:-GET}"
environment="${ARATIRI_ENVIRONMENT:-staging}"
network="${ARATIRI_NETWORK:-regtest}"
if [[ "${WORKLOAD:-transactions}" == "custom" \
  && "${request_method^^}" != "GET" \
  && "${request_method^^}" != "HEAD" \
  && "${request_method^^}" != "OPTIONS" ]]; then
  if [[ "${ARATIRI_LOAD_CONFIRM:-}" != "I_ACCEPT_NON_PRODUCTION_MUTATION_LOAD" ]]; then
    echo "Mutation load requires ARATIRI_LOAD_CONFIRM=I_ACCEPT_NON_PRODUCTION_MUTATION_LOAD" >&2
    exit 2
  fi
  if [[ "${ARATIRI_NO_REAL_FUNDS_CONFIRM:-}" != "NO_REAL_FUNDS_OR_PII" ]]; then
    echo "Mutation load requires ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII" >&2
    exit 2
  fi
  if [[ "${environment,,}" == "production" \
    || "${environment,,}" == "testnet" \
    || "${network,,}" == "mainnet" \
    || "${network,,}" == "testnet" ]]; then
    echo "Mutation load is prohibited on production, mainnet, and testnet" >&2
    exit 2
  fi
fi

if [[ "${POLL_TERMINAL:-false}" == "true" && -z "${TOKEN:-}" ]]; then
  echo "TOKEN is required when POLL_TERMINAL=true" >&2
  exit 2
fi

mkdir -p "$result_dir"

env_args=()
for name in \
  BASE_URL TOKEN WORKLOAD REQUEST_METHOD REQUEST_PATH REQUEST_BODY CONTENT_TYPE \
  EXPECTED_STATUSES STEP_DURATION MAX_ACCEPTANCE_P95_MS MAX_ERROR_RATE \
  MIN_THROUGHPUT_RPS THINK_TIME_SECONDS REQUEST_TIMEOUT POLL_TERMINAL \
  ACCEPTANCE_ID_PATH SETTLEMENT_PATH_TEMPLATE SETTLEMENT_STATE_PATH \
  POLL_INTERVAL_SECONDS SETTLEMENT_TIMEOUT_SECONDS \
  MAX_SETTLEMENT_ERROR_RATE; do
  if [[ -v "$name" ]]; then
    env_args+=(--env "$name")
  fi
done

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
commit="$(git -C "$repo_dir" rev-parse HEAD 2>/dev/null || printf unknown)"
if [[ -z "$(git -C "$repo_dir" status --porcelain 2>/dev/null)" ]]; then
  dirty=false
else
  dirty=true
fi

cat >"$result_dir/run-metadata.json" <<EOF
{
  "experiment": "VAL-04",
  "started_at": "$started_at",
  "commit": "$commit",
  "working_tree_dirty": $dirty,
  "tool_image": "$image",
  "workload": "${WORKLOAD:-transactions}",
  "request_method": "${request_method^^}",
  "environment": "$environment",
  "network": "$network",
  "base_url": "${BASE_URL:-http://host.docker.internal:2100}",
  "step_duration": "${STEP_DURATION:-10m}",
  "vus": [10, 25, 50, 100],
  "poll_terminal_settlement": ${POLL_TERMINAL:-false},
  "secrets_recorded": false
}
EOF

echo "Writing VAL-04 raw evidence to $result_dir"
set +e
docker run --rm \
  --add-host host.docker.internal:host-gateway \
  --volume "$script_dir:/scripts:ro" \
  --volume "$result_dir:/results:rw" \
  "${env_args[@]}" \
  --env SUMMARY_PATH=/results/summary.json \
  "$image" run \
  --out json=/results/raw.json \
  /scripts/load.js
exit_code=$?
set -e

ended_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '%s\n' "$ended_at" >"$result_dir/ended-at.txt"
printf '%s\n' "$exit_code" >"$result_dir/runner-exit-code.txt"

if (( exit_code != 0 )); then
  echo "k6 failed or a frozen threshold was crossed (exit $exit_code)." >&2
  exit "$exit_code"
fi
echo "k6 completed. Execution alone does not establish PFGR compliance."
