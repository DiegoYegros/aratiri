#!/usr/bin/env bash
# Deploy-hold helpers for deploy.sh (sourced; no side effects).
#
# When active, the poller should exit 0 without pulling so the systemd timer
# can stay enabled while GHCR :latest lags local Batch images.

# True if env ARATIRI_DEPLOY_HOLD=1 or ${compose_dir}/.deploy.hold exists.
# Usage: is_deploy_hold_active <compose-dir>
is_deploy_hold_active() {
  local compose_dir="${1:-}"
  if [ "${ARATIRI_DEPLOY_HOLD:-}" = "1" ]; then
    return 0
  fi
  if [ -n "${compose_dir}" ] && [ -f "${compose_dir}/.deploy.hold" ]; then
    return 0
  fi
  return 1
}
