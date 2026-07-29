#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
target="${1:-}"
duration="${OUTAGE_DURATION_SECONDS:-60}"
recovery_timeout="${RECOVERY_TIMEOUT_SECONDS:-180}"
health_url="${HEALTH_URL:-http://127.0.0.1:2100/actuator/health}"
prometheus_url="${PROMETHEUS_URL:-http://127.0.0.1:2100/actuator/prometheus}"
run_id="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="${RESULT_DIR:-$script_dir/results/${target:-unknown}-$run_id}"

case "$target" in
  kafka) service="${FAULT_SERVICE:-kafka}" ;;
  db) service="${FAULT_SERVICE:-db}" ;;
  lnd) service="${FAULT_SERVICE:-lnd-alice}" ;;
  *)
    echo "Usage: $0 kafka|db|lnd" >&2
    exit 2
    ;;
esac

if [[ "${ARATIRI_FAULT_CONFIRM:-}" != "I_ACCEPT_NON_PRODUCTION_OUTAGE" ]]; then
  echo "Refusing outage: set ARATIRI_FAULT_CONFIRM=I_ACCEPT_NON_PRODUCTION_OUTAGE" >&2
  exit 2
fi
if [[ "${ARATIRI_NO_REAL_FUNDS_CONFIRM:-}" != "NO_REAL_FUNDS_OR_PII" ]]; then
  echo "Refusing outage: set ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII" >&2
  exit 2
fi
environment="${ARATIRI_ENVIRONMENT:-regtest}"
network="${ARATIRI_NETWORK:-regtest}"
if [[ "${environment,,}" == "production" \
  || "${environment,,}" == "testnet" \
  || "${network,,}" == "mainnet" \
  || "${network,,}" == "testnet" ]]; then
  echo "Production, mainnet, and testnet outages are prohibited by this runner" >&2
  exit 2
fi
if [[ -z "${PROBE_BEARER_TOKEN:-}" \
  && "${PUBLIC_PROBES_CONFIRM:-}" != "I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC" ]]; then
  echo "Set PROBE_BEARER_TOKEN, or explicitly confirm public probes with PUBLIC_PROBES_CONFIRM=I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC" >&2
  exit 2
fi
if [[ ! "$duration" =~ ^[0-9]+$ ]] || (( duration < 1 || duration > 3600 )); then
  echo "OUTAGE_DURATION_SECONDS must be an integer from 1 to 3600" >&2
  exit 2
fi
if [[ ! "$recovery_timeout" =~ ^[0-9]+$ ]] || (( recovery_timeout < 1 || recovery_timeout > 3600 )); then
  echo "RECOVERY_TIMEOUT_SECONDS must be an integer from 1 to 3600" >&2
  exit 2
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 2
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required" >&2
  exit 2
fi

if [[ -n "${FAULT_COMPOSE_FILES:-}" ]]; then
  compose_files="$FAULT_COMPOSE_FILES"
  compose_env_file="${FAULT_ENV_FILE:-}"
elif [[ "$target" == "lnd" ]]; then
  compose_files="validation/regtest/compose.yml"
  compose_env_file="validation/regtest/runtime/compose.env"
else
  compose_files="docker-compose.yml"
  compose_env_file=""
fi

compose_args=()
if [[ -n "$compose_env_file" ]]; then
  if [[ "$compose_env_file" != /* ]]; then
    compose_env_file="$repo_dir/$compose_env_file"
  fi
  if [[ ! -f "$compose_env_file" ]]; then
    echo "Compose env file does not exist: $compose_env_file" >&2
    exit 2
  fi
  compose_args+=(--env-file "$compose_env_file")
fi
IFS=: read -r -a compose_file_array <<<"$compose_files"
for file in "${compose_file_array[@]}"; do
  if [[ "$file" != /* ]]; then
    file="$repo_dir/$file"
  fi
  if [[ ! -f "$file" ]]; then
    echo "Compose file does not exist: $file" >&2
    exit 2
  fi
  compose_args+=(-f "$file")
done
compose_args+=(--profile "*")

curl_auth_args=()
if [[ -n "${PROBE_BEARER_TOKEN:-}" ]]; then
  curl_auth_args+=(--header "Authorization: Bearer $PROBE_BEARER_TOKEN")
fi

mkdir -p "$result_dir"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch="$(date -u +%s)"
commit="$(git -C "$repo_dir" rev-parse HEAD 2>/dev/null || printf unknown)"
if [[ -z "$(git -C "$repo_dir" status --porcelain 2>/dev/null)" ]]; then
  dirty=false
else
  dirty=true
fi

cat >"$result_dir/run-metadata.json" <<EOF
{
  "experiment": "VAL-06",
  "started_at": "$started_at",
  "commit": "$commit",
  "working_tree_dirty": $dirty,
  "target": "$target",
  "compose_service": "$service",
  "environment": "$environment",
  "network": "$network",
  "planned_outage_seconds": $duration,
  "recovery_timeout_seconds": $recovery_timeout,
  "health_url": "$health_url",
  "prometheus_url": "$prometheus_url",
  "real_funds_or_pii": false,
  "secrets_recorded": false
}
EOF

if ! preflight_observation="$(curl -sS --max-time 10 \
  "${curl_auth_args[@]}" \
  --output "$result_dir/preflight-health.body" \
  --write-out '%{http_code} %{time_total}' \
  "$health_url" 2>"$result_dir/preflight-health.stderr")"; then
  preflight_observation="000 0"
fi
read -r preflight_code preflight_seconds <<<"$preflight_observation"
printf '{"http_code":"%s","time_total":%s}\n' \
  "$preflight_code" "${preflight_seconds:-0}" \
  >"$result_dir/preflight-health-observation.json"
if [[ ! "$preflight_code" =~ ^2[0-9][0-9]$ ]]; then
  echo "Pre-outage health probe returned $preflight_code; refusing to stop '$service'." >&2
  exit 2
fi

compose() {
  docker compose "${compose_args[@]}" "$@"
}

capture_phase() {
  local phase="$1"
  local phase_dir="$result_dir/$phase"
  mkdir -p "$phase_dir"
  date -u +%Y-%m-%dT%H:%M:%SZ >"$phase_dir/timestamp.txt"
  compose ps "$service" --format json \
    >"$phase_dir/service-state.json" \
    2>"$phase_dir/service-state.stderr" || true
  curl -sS --max-time 10 \
    "${curl_auth_args[@]}" \
    --output "$phase_dir/health.body" \
    --write-out '{"http_code":"%{http_code}","time_total":%{time_total}}\n' \
    "$health_url" \
    >"$phase_dir/health-observation.json" \
    2>"$phase_dir/health.stderr" || true
  curl -fsS --max-time 15 "${curl_auth_args[@]}" "$prometheus_url" \
    >"$phase_dir/prometheus.txt" \
    2>"$phase_dir/prometheus.stderr" || true
  if [[ -n "${SERVICE_PROBE_URL:-}" ]]; then
    curl -sS --max-time 10 \
      "${curl_auth_args[@]}" \
      --output "$phase_dir/service-probe.body" \
      --write-out '{"http_code":"%{http_code}","time_total":%{time_total}}\n' \
      "$SERVICE_PROBE_URL" \
      >"$phase_dir/service-probe-observation.json" \
      2>"$phase_dir/service-probe.stderr" || true
  fi
}

recovery_needed=false
recovered=false
cleanup() {
  local exit_code=$?
  if [[ "$recovery_needed" == true ]]; then
    echo "Emergency recovery: starting compose service '$service'." >&2
    compose start "$service" >>"$result_dir/recovery-command.log" 2>&1 || true
  fi
  return "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

echo "Capturing pre-outage state in $result_dir"
capture_phase before

outage_started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
outage_started_epoch="$(date -u +%s)"
if ! compose stop -t "${COMPOSE_STOP_TIMEOUT_SECONDS:-10}" "$service" \
  >"$result_dir/outage-command.log" 2>&1; then
  echo "Could not stop '$service'; no outage result may be claimed." >&2
  exit 1
fi
recovery_needed=true
capture_phase during
echo "Controlled $target outage active for $duration seconds."
sleep "$duration"

recovery_started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
recovery_started_epoch="$(date -u +%s)"
if ! compose start "$service" >"$result_dir/recovery-command.log" 2>&1; then
  echo "Initial recovery command failed; the EXIT trap will retry." >&2
  capture_phase recovery-command-failed
  exit 1
fi

deadline=$(( $(date -u +%s) + recovery_timeout ))
attempt=0
while (( $(date -u +%s) <= deadline )); do
  attempt=$((attempt + 1))
  observed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  running_services="$(compose ps --status running --services "$service" 2>/dev/null || true)"
  if ! app_code="$(curl -sS --max-time 10 \
    "${curl_auth_args[@]}" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$health_url" 2>/dev/null)"; then
    app_code=000
  fi
  service_running=false
  while IFS= read -r running_service; do
    [[ "$running_service" == "$service" ]] && service_running=true
  done <<<"$running_services"
  printf '{"attempt":%d,"observed_at":"%s","service_running":%s,"health_http_code":"%s"}\n' \
    "$attempt" "$observed_at" "$service_running" "$app_code" \
    >>"$result_dir/recovery-observations.jsonl"
  if [[ "$service_running" == true && "$app_code" =~ ^2[0-9][0-9]$ ]]; then
    recovered=true
    recovered_at_epoch="$(date -u +%s)"
    break
  fi
  sleep "${RECOVERY_POLL_SECONDS:-5}"
done

capture_phase after
compose logs --no-color --since "$started_at" "$service" \
  >"$result_dir/service.log" 2>"$result_dir/service-log.stderr" || true

ended_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ended_epoch="$(date -u +%s)"
observed_outage_seconds=$((ended_epoch - outage_started_epoch))
if [[ "$recovered" == true ]]; then
  observed_recovery_seconds=$((recovered_at_epoch - recovery_started_epoch))
else
  observed_recovery_seconds=$((ended_epoch - recovery_started_epoch))
fi

cat >"$result_dir/summary.json" <<EOF
{
  "experiment": "VAL-06",
  "target": "$target",
  "started_at": "$started_at",
  "outage_started_at": "$outage_started_at",
  "recovery_started_at": "$recovery_started_at",
  "ended_at": "$ended_at",
  "planned_outage_seconds": $duration,
  "wall_clock_since_outage_seconds": $observed_outage_seconds,
  "observed_recovery_seconds": $observed_recovery_seconds,
  "recovered_within_runner_timeout": $recovered,
  "recovery_observation_attempts": $attempt,
  "criteria_frozen": false,
  "result": "INCONCLUSIVE_PENDING_CORRECTNESS_REVIEW",
  "limitations": [
    "Service-running and HTTP-health observations do not prove RPO, correct final state, retry behavior, or absence of duplicate effects.",
    "RPO/RTO thresholds remain subject to stakeholder approval."
  ]
}
EOF

if [[ "$recovered" != true ]]; then
  echo "Recovery was not observed before timeout; service start will be retried by the EXIT trap." >&2
  exit 1
fi
recovery_needed=false
echo "Recovery observed. Review DB/events/ledger/retries before assigning VAL-06 pass/fail."
