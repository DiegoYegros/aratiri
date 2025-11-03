#!/usr/bin/env bash
set -euo pipefail

OTEL_ENDPOINT="${1:-http://localhost:4318/v1/logs}"
SERVICE_NAME="${SERVICE_NAME:-debug-client}"
SCOPE_NAME="${SCOPE_NAME:-manual-debugger}"
SEVERITY="${SEVERITY:-INFO}"
MESSAGE="${MESSAGE:-Manual test log from send-test-log.sh}"

NOW_NANOS=$(date +%s%N)

cat <<JSON | curl -sS -f -X POST "${OTEL_ENDPOINT}" \
  -H "Content-Type: application/json" \
  --data-binary @-
{
  "resourceLogs": [
    {
      "resource": {
        "attributes": [
          { "key": "service.name", "value": { "stringValue": "${SERVICE_NAME}" } },
          { "key": "service.instance.id", "value": { "stringValue": "manual-${HOSTNAME:-local}" } },
          { "key": "telemetry.sdk.name", "value": { "stringValue": "manual" } }
        ]
      },
      "scopeLogs": [
        {
          "scope": { "name": "${SCOPE_NAME}", "version": "1.0.0" },
          "logRecords": [
            {
              "timeUnixNano": "${NOW_NANOS}",
              "observedTimeUnixNano": "${NOW_NANOS}",
              "severityText": "${SEVERITY}",
              "body": { "stringValue": "${MESSAGE}" },
              "attributes": [
                { "key": "debug.source", "value": { "stringValue": "manual-curl" } }
              ]
            }
          ]
        }
      ]
    }
  ]
}
JSON

echo "Sent test log with severity ${SEVERITY} to ${OTEL_ENDPOINT}" >&2
