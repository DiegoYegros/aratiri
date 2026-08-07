#!/usr/bin/env bash
# Non-interactive unit tests for ops/deploy libs (no Docker).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_image_refs.sh
source "${SCRIPT_DIR}/lib_image_refs.sh"
# shellcheck source=lib_deploy_hold.sh
source "${SCRIPT_DIR}/lib_deploy_hold.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { echo "ok: $*"; }

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

# --- is_qualified_aratiri_ghcr_ref ---
is_qualified_aratiri_ghcr_ref "ghcr.io/diegoyegros/aratiri:latest" || fail "latest tag"
is_qualified_aratiri_ghcr_ref "ghcr.io/diegoyegros/aratiri-frontend:latest" || fail "frontend latest"
is_qualified_aratiri_ghcr_ref "ghcr.io/diegoyegros/aratiri-admin@sha256:deadbeef" || fail "admin digest"
is_qualified_aratiri_ghcr_ref "aratiri-admin:latest" && fail "unqualified short name should be rejected"
is_qualified_aratiri_ghcr_ref "ghcr.io/other/aratiri:latest" && fail "wrong registry should be rejected"
pass "is_qualified_aratiri_ghcr_ref"

# --- aratiri_image_basename (short, GHCR, host:port) ---
[ "$(aratiri_image_basename 'aratiri-admin:v1')" = "aratiri-admin" ] || fail "short name basename"
[ "$(aratiri_image_basename 'ghcr.io/diegoyegros/aratiri:latest')" = "aratiri" ] || fail "GHCR tag basename"
[ "$(aratiri_image_basename 'ghcr.io/diegoyegros/aratiri-admin@sha256:deadbeef')" = "aratiri-admin" ] || fail "GHCR digest basename"
[ "$(aratiri_image_basename '127.0.0.1:5000/aratiri:latest')" = "aratiri" ] || fail "ported registry basename"
[ "$(aratiri_image_basename 'registry.local:5000/aratiri-admin:v1')" = "aratiri-admin" ] || fail "ported registry admin basename"
pass "aratiri_image_basename"

# --- assert: good compose (matches ops/docker-compose.prod.yml image lines) ---
cat > "${tmpdir}/good.yml" <<'EOF'
services:
  db:
    image: postgres:16-alpine
  aratiri-app:
    image: ghcr.io/diegoyegros/aratiri:latest
  aratiri-frontend:
    image: ghcr.io/diegoyegros/aratiri-frontend:latest
  aratiri-admin:
    image: "ghcr.io/diegoyegros/aratiri-admin:latest"
EOF
assert_compose_aratiri_ghcr_images "${tmpdir}/good.yml" || fail "good compose should pass"
pass "assert good compose"

# --- assert: unqualified local tags fail-closed ---
cat > "${tmpdir}/bad.yml" <<'EOF'
services:
  aratiri-app:
    image: ghcr.io/diegoyegros/aratiri:latest
  aratiri-frontend:
    image: aratiri-frontend:latest
  aratiri-admin:
    image: aratiri-admin:v1
EOF
if out="$(assert_compose_aratiri_ghcr_images "${tmpdir}/bad.yml")"; then
  fail "bad compose should exit non-zero"
fi
echo "${out}" | grep -q 'unqualified Aratiri app image ref' || fail "expected explicit unqualified log line"
echo "${out}" | grep -q 'aratiri-frontend:latest' || fail "expected frontend ref in log"
echo "${out}" | grep -q 'aratiri-admin:v1' || fail "expected admin ref in log"
pass "assert unqualified fail-closed"

# --- assert: host:port registry refs must fail-closed (not bypass basename) ---
cat > "${tmpdir}/ported.yml" <<'EOF'
services:
  aratiri-app:
    image: 127.0.0.1:5000/aratiri:latest
  aratiri-admin:
    image: registry.local:5000/aratiri-admin:v1
EOF
if out="$(assert_compose_aratiri_ghcr_images "${tmpdir}/ported.yml")"; then
  fail "ported-registry compose should exit non-zero"
fi
echo "${out}" | grep -q '127.0.0.1:5000/aratiri:latest' || fail "expected ported aratiri ref in log"
echo "${out}" | grep -q 'registry.local:5000/aratiri-admin:v1' || fail "expected ported admin ref in log"
pass "assert ported-registry fail-closed"

# --- prod compose fixture (repo copy) ---
prod="${SCRIPT_DIR}/../docker-compose.prod.yml"
if [ -f "${prod}" ]; then
  assert_compose_aratiri_ghcr_images "${prod}" || fail "ops/docker-compose.prod.yml should pass"
  pass "assert ops/docker-compose.prod.yml"
fi

# --- is_deploy_hold_active (env + hold file) ---
unset ARATIRI_DEPLOY_HOLD || true
is_deploy_hold_active "${tmpdir}" && fail "no hold should be inactive"
touch "${tmpdir}/.deploy.hold"
is_deploy_hold_active "${tmpdir}" || fail "hold file should be active"
rm -f "${tmpdir}/.deploy.hold"
is_deploy_hold_active "${tmpdir}" && fail "removed hold file should be inactive"
ARATIRI_DEPLOY_HOLD=1
is_deploy_hold_active "${tmpdir}" || fail "ARATIRI_DEPLOY_HOLD=1 should be active"
ARATIRI_DEPLOY_HOLD=0
is_deploy_hold_active "${tmpdir}" && fail "ARATIRI_DEPLOY_HOLD=0 should be inactive"
unset ARATIRI_DEPLOY_HOLD || true
pass "is_deploy_hold_active"

echo "all tests passed"
