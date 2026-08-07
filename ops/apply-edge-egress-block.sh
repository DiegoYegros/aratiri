#!/usr/bin/env bash
# Block NEW outbound internet connections from the aratiri_edge Docker network
# while keeping host→container published ports working (ESTABLISHED replies).
#
# Requires root (or passwordless sudo for iptables). Idempotent.
set -euo pipefail

NET="${ARATIRI_EDGE_NETWORK:-aratiri_edge}"
CHAIN="DOCKER-USER"
COMMENT="aratiri-edge-egress-block"

if ! docker network inspect "$NET" >/dev/null 2>&1; then
  echo "network $NET not found; start the stack first" >&2
  exit 1
fi

SUBNET="$(docker network inspect "$NET" -f '{{(index .IPAM.Config 0).Subnet}}')"
if [[ -z "$SUBNET" || "$SUBNET" == "<no value>" ]]; then
  echo "could not resolve subnet for $NET" >&2
  exit 1
fi

run_ipt() {
  if [[ "$(id -u)" -eq 0 ]]; then
    iptables "$@"
  else
    sudo iptables "$@"
  fi
}

# Remove previous copies of our rule (match by comment).
while run_ipt -L "$CHAIN" -n --line-numbers 2>/dev/null | grep -F "$COMMENT" >/dev/null; do
  NUM="$(run_ipt -L "$CHAIN" -n --line-numbers | awk -v c="$COMMENT" '$0 ~ c {print $1; exit}')"
  [[ -n "$NUM" ]] || break
  run_ipt -D "$CHAIN" "$NUM"
done

# Allow established/related first (published-port replies).
run_ipt -I "$CHAIN" 1 -s "$SUBNET" -m conntrack --ctstate ESTABLISHED,RELATED -m comment --comment "${COMMENT}-est" -j RETURN
# Drop new flows leaving the edge subnet (internet + other bridges).
run_ipt -I "$CHAIN" 2 -s "$SUBNET" -m conntrack --ctstate NEW -m comment --comment "$COMMENT" -j DROP

echo "Applied edge egress block for $NET ($SUBNET) on $CHAIN"
run_ipt -L "$CHAIN" -n -v | sed -n "1,20p"
