#!/usr/bin/env bash
# Prove Docker network isolation for the Aratiri production stack.
# Exit 0 only if isolation and required connectivity checks pass.
set -euo pipefail

fail=0
pass() { echo "PASS  $*"; }
bad()  { echo "FAIL  $*"; fail=1; }

expect_fail() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    bad "$desc (command unexpectedly succeeded)"
  else
    pass "$desc"
  fi
}

expect_ok() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    pass "$desc"
  else
    bad "$desc"
  fi
}

echo "== Isolation: frontends have no internet / no data-plane =="
for c in aratiri-frontend aratiri-admin; do
  expect_fail "$c cannot reach 1.1.1.1:443" \
    docker exec "$c" wget -qO- --timeout=3 https://1.1.1.1
  expect_fail "$c cannot reach db:5432" \
    docker exec "$c" sh -c 'wget -qO- --timeout=2 http://db:5432/ || nc -z -w2 db 5432'
  expect_fail "$c cannot reach kafka:29092" \
    docker exec "$c" sh -c 'nc -z -w2 kafka 29092'
  expect_fail "$c cannot reach aratiri-backend:2100" \
    docker exec "$c" sh -c 'wget -qO- --timeout=2 http://aratiri-backend:2100/ || nc -z -w2 aratiri-backend 2100'
done

echo "== Isolation: data plane has no internet =="
expect_fail "postgres cannot reach 1.1.1.1:443" \
  docker exec aratiri-postgres wget -qO- --timeout=3 https://1.1.1.1
expect_fail "kafka cannot reach example.com:443" \
  docker exec aratiri-kafka bash -c 'timeout 3 bash -c "</dev/tcp/example.com/443"'

echo "== Connectivity: backend east-west =="
expect_ok "backend → db:5432" \
  docker exec aratiri-backend sh -c 'timeout 3 bash -c "</dev/tcp/db/5432"'
expect_ok "backend → kafka:29092" \
  docker exec aratiri-backend sh -c 'timeout 3 bash -c "</dev/tcp/kafka/29092"'
expect_ok "backend → lnd:10009" \
  docker exec aratiri-backend sh -c 'timeout 3 bash -c "</dev/tcp/lnd/10009"'

echo "== Connectivity: backend egress (legitimate) =="
expect_ok "backend → api.coingecko.com:443" \
  docker exec aratiri-backend sh -c 'timeout 5 bash -c "</dev/tcp/api.coingecko.com/443"'

echo "== Host loopback + public FE/API path =="
fe_code="$(curl -sS -m5 -o /dev/null -w '%{http_code}' http://127.0.0.1:3100/ || true)"
ad_code="$(curl -sS -m5 -o /dev/null -w '%{http_code}' http://127.0.0.1:3101/ || true)"
api_health_code="$(curl -sS -m5 -o /dev/null -w '%{http_code}' http://127.0.0.1:2100/actuator/health || true)"
api_prom_code="$(curl -sS -m5 -o /tmp/aratiri-loopback-prom.body -w '%{http_code}' http://127.0.0.1:2100/actuator/prometheus || true)"
[[ "$fe_code" =~ ^(200|307|308)$ ]] && pass "loopback frontend HTTP $fe_code" || bad "loopback frontend HTTP $fe_code"
[[ "$ad_code" =~ ^(200|307|308)$ ]] && pass "loopback admin HTTP $ad_code" || bad "loopback admin HTTP $ad_code"
# Unauthenticated health must succeed on the process (not JWT 401).
[[ "$api_health_code" == "200" ]] && pass "loopback /actuator/health HTTP $api_health_code" || bad "loopback /actuator/health HTTP $api_health_code (expected 200)"
# Prometheus scrape on loopback is optional to assert body; 200 is expected when up.
if [[ "$api_prom_code" == "200" ]]; then
  pass "loopback /actuator/prometheus HTTP $api_prom_code"
else
  pass "loopback /actuator/prometheus HTTP $api_prom_code (optional; not failing)"
fi

is_prometheus_metrics_body() {
  # Prometheus text exposition typically has HELP/TYPE or metric lines with braces/#.
  local body="$1"
  [[ -s "$body" ]] || return 1
  grep -qE '^(# (HELP|TYPE) |[a-zA-Z_:][a-zA-Z0-9_:]*)' "$body"
}

if command -v curl >/dev/null; then
  pub_fe="$(curl -sS -m15 -o /dev/null -w '%{http_code}' https://aratiri.net/ || true)"
  pub_health_body="$(mktemp)"
  pub_health="$(curl -sS -m15 -o "$pub_health_body" -w '%{http_code}' https://api.aratiri.net/actuator/health || true)"
  pub_prom_body="$(mktemp)"
  pub_prom="$(curl -sS -m15 -o "$pub_prom_body" -w '%{http_code}' https://api.aratiri.net/actuator/prometheus || true)"
  pub_metrics_body="$(mktemp)"
  pub_metrics="$(curl -sS -m15 -o "$pub_metrics_body" -w '%{http_code}' https://api.aratiri.net/actuator/metrics || true)"
  cors="$(curl -sS -m15 -D - -o /dev/null -X OPTIONS \
    -H 'Origin: https://aratiri.net' \
    -H 'Access-Control-Request-Method: GET' \
    https://api.aratiri.net/v1/ 2>/dev/null | tr -d '\r' | grep -i '^access-control-allow-origin: https://aratiri.net$' || true)"
  [[ "$pub_fe" == "200" ]] && pass "public https://aratiri.net → $pub_fe" || bad "public https://aratiri.net → $pub_fe"
  # Public health may be 200 (allowed) or edge-denied; JWT 401 alone is not a success signal.
  if [[ "$pub_health" == "200" ]] || [[ "$pub_health" =~ ^(403|404)$ ]]; then
    pass "public /actuator/health HTTP $pub_health"
  else
    bad "public /actuator/health HTTP $pub_health (expected 200 or edge 403/404; not JWT-only gate)"
  fi
  # Public prometheus must not be an open scrape (non-200, empty, or non-metrics body).
  if [[ "$pub_prom" == "200" ]] && is_prometheus_metrics_body "$pub_prom_body"; then
    bad "public /actuator/prometheus returned 200 with Prometheus metrics body (must be edge-denied)"
  else
    pass "public /actuator/prometheus not an open scrape (HTTP $pub_prom)"
  fi
  if [[ "$pub_metrics" == "200" ]] && grep -qiE 'names|"name"' "$pub_metrics_body" 2>/dev/null; then
    bad "public /actuator/metrics returned 200 with metrics catalog (must be edge-denied or auth)"
  else
    pass "public /actuator/metrics not an open catalog (HTTP $pub_metrics)"
  fi
  [[ -n "$cors" ]] && pass "CORS allow-origin for https://aratiri.net" || bad "CORS allow-origin for https://aratiri.net"
  rm -f "$pub_health_body" "$pub_prom_body" "$pub_metrics_body"
fi
rm -f /tmp/aratiri-loopback-prom.body

echo "== Host DOCKER-USER edge egress block =="
if sudo -n iptables -L DOCKER-USER -n 2>/dev/null | grep -q 'aratiri-edge-egress-block'; then
  pass "DOCKER-USER has aratiri-edge-egress-block rule"
elif iptables -L DOCKER-USER -n 2>/dev/null | grep -q 'aratiri-edge-egress-block'; then
  pass "DOCKER-USER has aratiri-edge-egress-block rule"
else
  bad "DOCKER-USER missing aratiri-edge-egress-block (run ops/apply-edge-egress-block.sh)"
fi

echo
if [[ "$fail" -eq 0 ]]; then
  echo "All network isolation checks passed."
  exit 0
fi
echo "One or more checks failed."
exit 1
