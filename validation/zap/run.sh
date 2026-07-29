#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
image="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:2.15.0}"
mode="${1:-baseline}"
run_id="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="${RESULT_DIR:-$script_dir/results/$run_id}"

if [[ "$mode" != "baseline" && "$mode" != "full" ]]; then
  echo "Usage: $0 [baseline|full]" >&2
  exit 2
fi
if [[ -z "${ZAP_TARGET_URL:-}" ]]; then
  echo "ZAP_TARGET_URL is required" >&2
  exit 2
fi
if [[ -z "${ZAP_BEARER_TOKEN:-}" && -z "${ZAP_AUTH_HEADER_VALUE:-}" ]]; then
  echo "Set ZAP_BEARER_TOKEN or ZAP_AUTH_HEADER_VALUE through the environment" >&2
  exit 2
fi
if [[ "${ZAP_CONFIRM_AUTHORIZED_TARGET:-}" != "I_HAVE_WRITTEN_AUTHORIZATION" ]]; then
  echo "Refusing scan: set ZAP_CONFIRM_AUTHORIZED_TARGET=I_HAVE_WRITTEN_AUTHORIZATION" >&2
  exit 2
fi
if [[ "$mode" == "full" && "${ZAP_CONFIRM_ACTIVE_SCAN:-}" != "I_ACCEPT_INTRUSIVE_ACTIVE_SCAN" ]]; then
  echo "Full scan is intrusive; set ZAP_CONFIRM_ACTIVE_SCAN=I_ACCEPT_INTRUSIVE_ACTIVE_SCAN" >&2
  exit 2
fi
environment="${ARATIRI_ENVIRONMENT:-}"
network="${ARATIRI_NETWORK:-}"
if [[ "$mode" == "full" ]]; then
  if [[ -z "$environment" || -z "$network" ]]; then
    echo "Full scan requires explicit ARATIRI_ENVIRONMENT and ARATIRI_NETWORK" >&2
    exit 2
  fi
  if [[ "${environment,,}" == "production" \
    || "${environment,,}" == "mainnet" \
    || "${environment,,}" == "testnet" \
    || "${network,,}" == "production" \
    || "${network,,}" == "mainnet" \
    || "${network,,}" == "testnet" ]]; then
    echo "Full active scan is prohibited on production, mainnet, and testnet" >&2
    exit 2
  fi
fi
if [[ "$ZAP_TARGET_URL" != https://* && "${ZAP_ALLOW_HTTP_LAB:-}" != "I_ACCEPT_HTTP_IN_ISOLATED_LAB" ]]; then
  echo "Target must use HTTPS unless ZAP_ALLOW_HTTP_LAB confirms an isolated lab" >&2
  exit 2
fi

ZAP_TARGET_URL="${ZAP_TARGET_URL%/}"
if [[ -n "${ZAP_AUTH_HEADER_VALUE:-}" ]]; then
  auth_header="$ZAP_AUTH_HEADER_VALUE"
else
  auth_header="Bearer $ZAP_BEARER_TOKEN"
fi
if [[ "$auth_header" == *$'\n'* || "$auth_header" == *$'\r'* || "$auth_header" == *'"'* ]]; then
  echo "Authorization header contains unsupported control/quote characters" >&2
  exit 2
fi
export ZAP_AUTH_HEADER_VALUE="$auth_header"
export ZAP_TARGET_URL

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

authenticated_curl() {
  printf 'header = "Authorization: %s"\n' "$ZAP_AUTH_HEADER_VALUE" \
    | curl --config - "$@"
}

mkdir -p "$result_dir"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
commit="$(git -C "$repo_dir" rev-parse HEAD 2>/dev/null || printf unknown)"
if [[ -z "$(git -C "$repo_dir" status --porcelain 2>/dev/null)" ]]; then
  dirty=false
else
  dirty=true
fi
cat >"$result_dir/run-metadata.json" <<EOF
{
  "experiment": "VAL-08",
  "started_at": "$started_at",
  "commit": "$commit",
  "working_tree_dirty": $dirty,
  "tool_image": "$image",
  "mode": "$mode",
  "target": "$ZAP_TARGET_URL",
  "environment": "$(json_escape "$environment")",
  "network": "$(json_escape "$network")",
  "authenticated": true,
  "secrets_recorded": false
}
EOF

coverage_routes="${ZAP_API_ROUTES:-/v1/auth/me,/v1/accounts/account,/v1/transactions?limit=1,/v1/admin/node-info}"
coverage_total=0
coverage_success=0
IFS=, read -r -a coverage_route_array <<<"$coverage_routes"
for route in "${coverage_route_array[@]}"; do
  if [[ "$route" != /v1/* ]]; then
    echo "Every ZAP_API_ROUTES entry must begin with /v1/: $route" >&2
    exit 2
  fi
  coverage_total=$((coverage_total + 1))
  if ! observation="$(authenticated_curl -sS --max-time 15 \
    --output /dev/null \
    --write-out '%{http_code} %{time_total}' \
    "$ZAP_TARGET_URL$route" 2>>"$result_dir/coverage.stderr")"; then
    observation="000 0"
  fi
  read -r http_code time_total <<<"$observation"
  authenticated_success=false
  if [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
    authenticated_success=true
    coverage_success=$((coverage_success + 1))
  fi
  printf '{"route":"%s","http_code":"%s","time_total":%s,"authenticated_success":%s}\n' \
    "$(json_escape "$route")" "$http_code" "${time_total:-0}" "$authenticated_success" \
    >>"$result_dir/authenticated-v1-coverage.jsonl"
done

coverage_result=AUTHENTICATED_V1_COVERAGE_CONFIRMED
if (( coverage_success == 0 )); then
  coverage_result=INCONCLUSIVE_NO_AUTHENTICATED_V1_COVERAGE
fi
cat >"$result_dir/coverage-summary.json" <<EOF
{
  "result": "$coverage_result",
  "routes_attempted": $coverage_total,
  "authenticated_2xx_routes": $coverage_success,
  "openapi_required": false,
  "deterministic_requestor_seeded": true
}
EOF
if (( coverage_success == 0 )); then
  date -u +%Y-%m-%dT%H:%M:%SZ >"$result_dir/ended-at.txt"
  printf '3\n' >"$result_dir/runner-exit-code.txt"
  echo "No authenticated /v1 route returned 2xx; VAL-08 is inconclusive and ZAP was not started." >&2
  exit 3
fi

echo "Running authorized $mode DAST; evidence directory: $result_dir"
set +e
docker run --rm \
  --volume "$script_dir:/zap/plan:ro" \
  --volume "$result_dir:/zap/wrk/results:rw" \
  --env ZAP_TARGET_URL \
  --env ZAP_AUTH_HEADER_VALUE \
  "$image" zap.sh -cmd -autorun "/zap/plan/plan-$mode.yaml" \
  >"$result_dir/zap-console.log" 2>&1
exit_code=$?
set -e

date -u +%Y-%m-%dT%H:%M:%SZ >"$result_dir/ended-at.txt"
printf '%s\n' "$exit_code" >"$result_dir/runner-exit-code.txt"
if (( exit_code != 0 )); then
  echo "ZAP automation failed (exit $exit_code); inspect zap-console.log." >&2
  exit "$exit_code"
fi
echo "ZAP completed. Classify findings and record fixes/acceptances in the VAL-08 manifest."
