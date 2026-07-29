#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_command bash
require_command docker

"${SCRIPT_DIR}/prepare.sh" >/dev/null
load_lab_environment

for script in "${SCRIPT_DIR}"/*.sh; do
  bash -n "${script}"
done

# Config is checked but no images are pulled and no containers are started.
dc config --quiet

note "Shell syntax and Docker Compose configuration are valid."
note "No containers were started and no images were pulled."
