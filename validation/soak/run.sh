#!/usr/bin/env bash
set -uo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/../.." && pwd)"
duration="${SOAK_DURATION_SECONDS:-172800}"
interval="${SAMPLE_INTERVAL_SECONDS:-60}"
burst_interval="${BURST_INTERVAL_SECONDS:-3600}"
burst_requests="${BURST_REQUESTS:-20}"
base_url="${BASE_URL:-http://127.0.0.1:2100}"
health_url="${HEALTH_URL:-${base_url%/}/actuator/health}"
prometheus_url="${PROMETHEUS_URL:-${base_url%/}/actuator/prometheus}"
api_probe_url="${API_PROBE_URL:-$health_url}"
db_container="${DB_CONTAINER:-postgres_db}"
kafka_container="${KAFKA_CONTAINER:-kafka}"
containers="${CONTAINERS:-aratiri-backend,postgres_db,kafka}"
run_id="${RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="${RESULT_DIR:-$script_dir/results/$run_id}"

if [[ "${ARATIRI_SOAK_CONFIRM:-}" != "RUN_48H_NON_PRODUCTION" ]]; then
  echo "Refusing soak: set ARATIRI_SOAK_CONFIRM=RUN_48H_NON_PRODUCTION" >&2
  exit 2
fi
if [[ "${ARATIRI_NO_REAL_FUNDS_CONFIRM:-}" != "NO_REAL_FUNDS_OR_PII" ]]; then
  echo "Refusing soak: set ARATIRI_NO_REAL_FUNDS_CONFIRM=NO_REAL_FUNDS_OR_PII" >&2
  exit 2
fi
environment="${ARATIRI_ENVIRONMENT:-staging}"
network="${ARATIRI_NETWORK:-regtest}"
if [[ "${environment,,}" == "production" \
  || "${environment,,}" == "testnet" \
  || "${network,,}" == "mainnet" \
  || "${network,,}" == "testnet" ]]; then
  echo "Production, mainnet, and testnet soak runs are prohibited" >&2
  exit 2
fi
if [[ -z "${PROBE_BEARER_TOKEN:-}" \
  && "${PUBLIC_PROBES_CONFIRM:-}" != "I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC" ]]; then
  echo "Set PROBE_BEARER_TOKEN, or explicitly confirm public probes with PUBLIC_PROBES_CONFIRM=I_CONFIRM_ACTUATOR_PROBES_ARE_PUBLIC" >&2
  exit 2
fi
for value_name in duration interval burst_interval burst_requests; do
  value="${!value_name}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < 1 )); then
    echo "$value_name must be a positive integer" >&2
    exit 2
  fi
done
if ! command -v curl >/dev/null 2>&1 \
  || ! command -v docker >/dev/null 2>&1 \
  || ! command -v gzip >/dev/null 2>&1; then
  echo "curl, docker, and gzip are required" >&2
  exit 2
fi

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

mkdir -p "$result_dir/samples"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
started_epoch="$(date -u +%s)"
deadline=$((started_epoch + duration))
next_burst=$((started_epoch + burst_interval))
commit="$(git -C "$repo_dir" rev-parse HEAD 2>/dev/null || printf unknown)"
if [[ -z "$(git -C "$repo_dir" status --porcelain 2>/dev/null)" ]]; then
  dirty=false
else
  dirty=true
fi

cat >"$result_dir/run-metadata.json" <<EOF
{
  "experiment": "VAL-09",
  "started_at": "$started_at",
  "commit": "$commit",
  "working_tree_dirty": $dirty,
  "environment": "$(json_escape "$environment")",
  "network": "$(json_escape "$network")",
  "configured_duration_seconds": $duration,
  "sample_interval_seconds": $interval,
  "burst_interval_seconds": $burst_interval,
  "burst_requests": $burst_requests,
  "health_url": "$(json_escape "$health_url")",
  "prometheus_url": "$(json_escape "$prometheus_url")",
  "api_probe_url": "$(json_escape "$api_probe_url")",
  "containers": "$(json_escape "$containers")",
  "database_container": "$(json_escape "$db_container")",
  "kafka_container": "$(json_escape "$kafka_container")",
  "real_funds_or_pii": false,
  "secrets_recorded": false
}
EOF

curl_auth_args=()
if [[ -n "${PROBE_BEARER_TOKEN:-}" ]]; then
  curl_auth_args+=(--header "Authorization: Bearer $PROBE_BEARER_TOKEN")
fi

if ! initial_health_observation="$(curl -sS --max-time 10 \
  "${curl_auth_args[@]}" \
  --output "$result_dir/initial-health.body" \
  --write-out '%{http_code} %{time_total}' \
  "$health_url" 2>"$result_dir/initial-health.stderr")"; then
  initial_health_observation="000 0"
fi
read -r initial_health_code initial_health_seconds <<<"$initial_health_observation"
printf '{"http_code":"%s","time_total":%s}\n' \
  "$initial_health_code" "${initial_health_seconds:-0}" \
  >"$result_dir/initial-health-observation.json"
if [[ ! "$initial_health_code" =~ ^2[0-9][0-9]$ ]]; then
  echo "Initial health probe returned $initial_health_code; refusing to start the soak loop." >&2
  exit 2
fi

sample_total=0
health_healthy=0
health_failures=0
api_failures=0
metrics_failures=0
container_failures=0
db_backlog_failures=0
kafka_backlog_failures=0
burst_total=0
burst_failures=0
completed=false
finalized=false

finalize() {
  local exit_code=$?
  if [[ "$finalized" == true ]]; then
    return "$exit_code"
  fi
  finalized=true
  ended_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  ended_epoch="$(date -u +%s)"
  elapsed=$((ended_epoch - started_epoch))
  if (( sample_total > 0 )); then
    availability="$(awk -v ok="$health_healthy" -v total="$sample_total" 'BEGIN { printf "%.6f", (ok / total) * 100 }')"
  else
    availability="0.000000"
  fi
  status=interrupted
  if [[ "$completed" == true ]]; then
    status=completed
  fi
  cat >"$result_dir/summary.json" <<EOF
{
  "experiment": "VAL-09",
  "status": "$status",
  "started_at": "$started_at",
  "ended_at": "$ended_at",
  "configured_duration_seconds": $duration,
  "observed_elapsed_seconds": $elapsed,
  "sample_total": $sample_total,
  "health_healthy_samples": $health_healthy,
  "sample_based_availability_percent": $availability,
  "sample_failures": {
    "health": $health_failures,
    "api_probe": $api_failures,
    "prometheus": $metrics_failures,
    "container_state": $container_failures,
    "database_backlog": $db_backlog_failures,
    "kafka_backlog": $kafka_backlog_failures
  },
  "burst_total": $burst_total,
  "burst_failed_requests": $burst_failures,
  "criteria_frozen": false,
  "result": "INCONCLUSIVE_PENDING_48H_AND_INCIDENT_REVIEW",
  "limitations": [
    "Availability is sample-based and does not measure failures between observations.",
    "Prometheus, container, Kafka, or database probe failure can mean the observer failed rather than Aratiri failed.",
    "Health and low-traffic probes do not prove settlement correctness or absence of duplicate effects."
  ]
}
EOF
  return "$exit_code"
}
trap finalize EXIT
trap 'exit 130' INT TERM

echo "Starting VAL-09 observation for $duration seconds; evidence: $result_dir"
while (( $(date -u +%s) < deadline )); do
  sample_total=$((sample_total + 1))
  sample_started_epoch="$(date -u +%s)"
  observed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  sample_dir="$result_dir/samples/$(printf '%06d' "$sample_total")"
  mkdir -p "$sample_dir"
  printf '%s\n' "$observed_at" >"$sample_dir/timestamp.txt"

  if ! health_code="$(curl -sS --max-time 10 \
    "${curl_auth_args[@]}" \
    --output "$sample_dir/health.body" \
    --write-out '%{http_code}' \
    "$health_url" 2>"$sample_dir/health.stderr")"; then
    health_code=000
  fi
  health_ok=false
  if [[ "$health_code" =~ ^2[0-9][0-9]$ ]]; then
    health_ok=true
    health_healthy=$((health_healthy + 1))
  else
    health_failures=$((health_failures + 1))
  fi

  if ! api_observation="$(curl -sS --max-time 15 \
    "${curl_auth_args[@]}" \
    --output "$sample_dir/api-probe.body" \
    --write-out '%{http_code} %{time_total}' \
    "$api_probe_url" 2>"$sample_dir/api-probe.stderr")"; then
    api_observation="000 0"
  fi
  read -r api_code api_seconds <<<"$api_observation"
  api_ok=false
  if [[ "$api_code" =~ ^2[0-9][0-9]$ ]]; then
    api_ok=true
  else
    api_failures=$((api_failures + 1))
  fi

  metrics_ok=false
  if curl -fsS --max-time 20 "${curl_auth_args[@]}" "$prometheus_url" 2>"$sample_dir/prometheus.stderr" \
    | gzip -c >"$sample_dir/prometheus.txt.gz"; then
    metrics_ok=true
  else
    metrics_failures=$((metrics_failures + 1))
  fi

  container_ok=true
  IFS=, read -r -a container_array <<<"$containers"
  for container in "${container_array[@]}"; do
    container="${container#"${container%%[![:space:]]*}"}"
    container="${container%"${container##*[![:space:]]}"}"
    [[ -z "$container" ]] && continue
    if ! docker inspect --format \
      '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"state":{"status":{{json .State.Status}},"running":{{json .State.Running}},"started_at":{{json .State.StartedAt}},"finished_at":{{json .State.FinishedAt}},"exit_code":{{json .State.ExitCode}},"health":{{if .State.Health}}{{json .State.Health.Status}}{{else}}null{{end}}},"restart_count":{{json .RestartCount}}}' \
      "$container" \
      >>"$sample_dir/containers.jsonl" \
      2>>"$sample_dir/containers.stderr"; then
      container_ok=false
    fi
  done
  if [[ "$container_ok" != true ]]; then
    container_failures=$((container_failures + 1))
  fi

  db_backlog_ok=false
  if docker exec "$db_container" sh -c \
    'PGOPTIONS="-c search_path=aratiri" psql -X -q -t -A -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT json_build_object('"'"'outbox_non_published'"'"', (SELECT count(*) FROM outbox_events WHERE publish_status <> '"'"'PUBLISHED'"'"'), '"'"'node_operations_non_terminal'"'"', (SELECT count(*) FROM node_operations WHERE status NOT IN ('"'"'SUCCEEDED'"'"','"'"'FAILED'"'"')), '"'"'node_operations_unknown'"'"', (SELECT count(*) FROM node_operations WHERE status = '"'"'UNKNOWN_OUTCOME'"'"'));"' \
    >"$sample_dir/database-backlog.json" \
    2>"$sample_dir/database-backlog.stderr"; then
    db_backlog_ok=true
  else
    db_backlog_failures=$((db_backlog_failures + 1))
  fi

  kafka_backlog_ok=false
  if docker exec "$kafka_container" kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --all-groups --describe \
    >"$sample_dir/kafka-consumer-groups.txt" \
    2>"$sample_dir/kafka-consumer-groups.stderr"; then
    kafka_backlog_ok=true
  else
    kafka_backlog_failures=$((kafka_backlog_failures + 1))
  fi

  burst_executed=false
  now_epoch="$(date -u +%s)"
  if (( now_epoch >= next_burst )); then
    burst_executed=true
    for ((request_number = 1; request_number <= burst_requests; request_number++)); do
      burst_total=$((burst_total + 1))
      if ! burst_observation="$(curl -sS --max-time 15 \
        "${curl_auth_args[@]}" \
        --output /dev/null \
        --write-out '%{http_code},%{time_total}' \
        "$api_probe_url" 2>>"$result_dir/bursts.stderr")"; then
        burst_observation="000,0"
      fi
      burst_code="${burst_observation%%,*}"
      if [[ ! "$burst_code" =~ ^2[0-9][0-9]$ ]]; then
        burst_failures=$((burst_failures + 1))
      fi
      printf '%s,%d,%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$request_number" "$burst_observation" \
        >>"$result_dir/bursts.csv"
    done
    while (( next_burst <= now_epoch )); do
      next_burst=$((next_burst + burst_interval))
    done
  fi

  printf '{"sequence":%d,"observed_at":"%s","health_ok":%s,"health_http_code":"%s","api_probe_ok":%s,"api_http_code":"%s","api_seconds":%s,"prometheus_ok":%s,"container_state_ok":%s,"database_backlog_ok":%s,"kafka_backlog_ok":%s,"burst_executed":%s}\n' \
    "$sample_total" "$observed_at" "$health_ok" "$health_code" "$api_ok" "$api_code" \
    "${api_seconds:-0}" "$metrics_ok" "$container_ok" "$db_backlog_ok" \
    "$kafka_backlog_ok" "$burst_executed" >>"$result_dir/samples.jsonl"

  next_sample=$((sample_started_epoch + interval))
  remaining_sleep=$((next_sample - $(date -u +%s)))
  if (( remaining_sleep > 0 )); then
    sleep "$remaining_sleep"
  fi
done

completed=true
echo "Configured soak duration reached. Review incidents and raw evidence before assigning VAL-09."
