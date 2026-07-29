import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const acceptanceLatency = new Trend("api_acceptance_latency_ms", true);
const acceptanceErrors = new Rate("api_acceptance_errors");
const acceptanceRequests = new Counter("api_acceptance_requests");
const terminalObservationLatency = new Trend("terminal_observation_latency_ms", true);
const successfulSettlementLatency = new Trend("successful_settlement_latency_ms", true);
const settlementErrors = new Rate("terminal_settlement_errors");
const terminalObservations = new Counter("terminal_observations");
const successfulSettlements = new Counter("successful_settlements");

function positiveNumber(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number`);
  }
  return value;
}

function nonNegativeNumber(name, fallback) {
  const value = Number(__ENV[name] || fallback);
  if (!Number.isFinite(value) || value < 0) {
    throw new Error(`${name} must be a non-negative number`);
  }
  return value;
}

const stepDuration = __ENV.STEP_DURATION || "10m";
const workload = __ENV.WORKLOAD || "transactions";
const baseUrl = (__ENV.BASE_URL || "http://host.docker.internal:2100").replace(/\/+$/, "");
const token = __ENV.TOKEN || "";
const expectedStatuses = new Set(
  (__ENV.EXPECTED_STATUSES || "200")
    .split(",")
    .map((status) => Number(status.trim()))
);
const maxAcceptanceP95Ms = positiveNumber("MAX_ACCEPTANCE_P95_MS", "500");
const maxErrorRate = nonNegativeNumber("MAX_ERROR_RATE", "0.01");
const minThroughputRps = positiveNumber("MIN_THROUGHPUT_RPS", "1");
const pollTerminal = (__ENV.POLL_TERMINAL || "false").toLowerCase() === "true";
const pollIntervalSeconds = positiveNumber("POLL_INTERVAL_SECONDS", "1");
const settlementTimeoutSeconds = positiveNumber("SETTLEMENT_TIMEOUT_SECONDS", "120");
const terminalStates = new Set(["COMPLETED", "FAILED"]);

const scenarios = {};
[
  [10, "0s"],
  [25, stepDuration],
  [50, "20m"],
  [100, "30m"],
].forEach(([vus, startTime]) => {
  // The default 10m duration permits simple 10m/20m/30m start offsets. A
  // non-default STEP_DURATION must use the supported Xm form.
  if (stepDuration !== "10m") {
    const match = stepDuration.match(/^(\d+)m$/);
    if (!match) {
      throw new Error("STEP_DURATION overrides must use whole minutes, for example 2m");
    }
    const multiplier = { 10: 0, 25: 1, 50: 2, 100: 3 }[vus];
    startTime = `${Number(match[1]) * multiplier}m`;
  }
  scenarios[`step_${vus}_vus`] = {
    executor: "constant-vus",
    exec: "acceptanceScenario",
    vus,
    duration: stepDuration,
    startTime,
    gracefulStop: "30s",
    tags: { vu_step: String(vus), workload },
  };
});

const thresholds = {
  api_acceptance_latency_ms: [
    `p(95)<${maxAcceptanceP95Ms}`,
  ],
  api_acceptance_errors: [
    `rate<${maxErrorRate}`,
  ],
  api_acceptance_requests: [
    `rate>=${minThroughputRps}`,
  ],
};

if (pollTerminal) {
  thresholds.terminal_settlement_errors = [
    `rate<${nonNegativeNumber("MAX_SETTLEMENT_ERROR_RATE", "0.1")}`,
  ];
}

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ["avg", "min", "med", "p(50)", "p(90)", "p(95)", "p(99)", "max"],
  noConnectionReuse: false,
  discardResponseBodies: false,
};

function workloadRequest() {
  switch (workload) {
    case "currencies":
      return { method: "GET", path: "/v1/general-data/currencies", body: null };
    case "transactions":
      return { method: "GET", path: "/v1/transactions?limit=50", body: null };
    case "custom":
      return {
        method: (__ENV.REQUEST_METHOD || "GET").toUpperCase(),
        path: __ENV.REQUEST_PATH || "/actuator/health",
        body: __ENV.REQUEST_BODY || null,
      };
    default:
      throw new Error(`Unknown WORKLOAD '${workload}'; use currencies, transactions, or custom`);
  }
}

function headers() {
  const result = {
    Accept: "application/json",
    "Content-Type": __ENV.CONTENT_TYPE || "application/json",
    "User-Agent": "aratiri-pfgr-k6/1",
  };
  if (token) {
    result.Authorization = `Bearer ${token}`;
  }
  return result;
}

function readPath(value, dottedPath) {
  return dottedPath.split(".").reduce((current, key) => {
    if (current === null || current === undefined) {
      return undefined;
    }
    return current[key];
  }, value);
}

function responseJson(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

function pollForTerminal(acceptanceResponse, acceptedAt, tags) {
  const body = responseJson(acceptanceResponse);
  const id = readPath(body, __ENV.ACCEPTANCE_ID_PATH || "transactionId");
  if (!id) {
    settlementErrors.add(true, { ...tags, reason: "missing_id" });
    return;
  }

  const template = __ENV.SETTLEMENT_PATH_TEMPLATE || "/v1/transactions/{id}";
  const settlementUrl = `${baseUrl}${template.replace("{id}", encodeURIComponent(String(id)))}`;
  const deadline = Date.now() + settlementTimeoutSeconds * 1000;
  while (Date.now() < deadline) {
    const response = http.get(settlementUrl, {
      headers: headers(),
      tags: { ...tags, phase: "settlement_poll" },
      responseType: "text",
    });
    if (response.status >= 200 && response.status < 300) {
      const state = readPath(
        responseJson(response),
        __ENV.SETTLEMENT_STATE_PATH || "status"
      );
      if (state && terminalStates.has(String(state).toUpperCase())) {
        const elapsed = Date.now() - acceptedAt;
        const terminalState = String(state).toUpperCase();
        terminalObservationLatency.add(elapsed, {
          ...tags,
          terminal_state: terminalState,
        });
        terminalObservations.add(1, { ...tags, terminal_state: terminalState });
        if (terminalState === "COMPLETED") {
          successfulSettlementLatency.add(elapsed, tags);
          successfulSettlements.add(1, tags);
          settlementErrors.add(false, tags);
        } else {
          settlementErrors.add(true, { ...tags, reason: "terminal_failed" });
        }
        return;
      }
    }
    sleep(pollIntervalSeconds);
  }
  settlementErrors.add(true, { ...tags, reason: "timeout" });
}

export function acceptanceScenario() {
  const request = workloadRequest();
  const tags = {
    endpoint: `${request.method} ${request.path.split("?")[0]}`,
    workload,
    phase: "api_acceptance",
  };
  const response = http.request(
    request.method,
    `${baseUrl}${request.path}`,
    request.body,
    {
      headers: headers(),
      tags,
      responseType: "text",
      timeout: __ENV.REQUEST_TIMEOUT || "30s",
    }
  );
  const acceptedAt = Date.now();
  const accepted = expectedStatuses.has(response.status);

  acceptanceRequests.add(1, tags);
  acceptanceLatency.add(response.timings.duration, tags);
  acceptanceErrors.add(!accepted, {
    ...tags,
    status: String(response.status),
  });
  check(response, {
    "acceptance status is expected": () => accepted,
  }, tags);

  if (pollTerminal && accepted) {
    pollForTerminal(response, acceptedAt, tags);
  }
  sleep(nonNegativeNumber("THINK_TIME_SECONDS", "0.2"));
}

function metricValue(data, name, field) {
  return data.metrics[name] && data.metrics[name].values
    ? data.metrics[name].values[field]
    : undefined;
}

export function handleSummary(data) {
  const lines = [
    "Aratiri PFGR VAL-04 summary",
    `acceptance p50=${metricValue(data, "api_acceptance_latency_ms", "p(50)")} ms`,
    `acceptance p95=${metricValue(data, "api_acceptance_latency_ms", "p(95)")} ms`,
    `acceptance p99=${metricValue(data, "api_acceptance_latency_ms", "p(99)")} ms`,
    `acceptance error rate=${metricValue(data, "api_acceptance_errors", "rate")}`,
    `acceptance throughput=${metricValue(data, "api_acceptance_requests", "rate")} req/s`,
    `terminal observation p50=${metricValue(data, "terminal_observation_latency_ms", "p(50)")} ms`,
    `terminal observation p95=${metricValue(data, "terminal_observation_latency_ms", "p(95)")} ms`,
    `terminal observation p99=${metricValue(data, "terminal_observation_latency_ms", "p(99)")} ms`,
    `successful settlement p50=${metricValue(data, "successful_settlement_latency_ms", "p(50)")} ms`,
    `successful settlement p95=${metricValue(data, "successful_settlement_latency_ms", "p(95)")} ms`,
    `successful settlement p99=${metricValue(data, "successful_settlement_latency_ms", "p(99)")} ms`,
    "",
  ];
  return {
    [__ENV.SUMMARY_PATH || "/results/summary.json"]: JSON.stringify(data, null, 2),
    stdout: lines.join("\n"),
  };
}
