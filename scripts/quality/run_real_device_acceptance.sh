#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ "$#" -lt 1 ]]; then
  echo "Usage: bash scripts/quality/run_real_device_acceptance.sh <prepare|finish|block|connected-helper|benchmark-round|benchmark-compare> [options]" >&2
  exit 2
fi
COMMAND="$1"
shift
if [[ "${COMMAND}" == "benchmark-round" ]]; then
  exec python3 "${ROOT_DIR}/scripts/quality/run_physical_startup_benchmarks.py" run-round --project-root "${ROOT_DIR}" "$@"
fi
if [[ "${COMMAND}" == "benchmark-compare" ]]; then
  exec python3 "${ROOT_DIR}/scripts/quality/run_physical_startup_benchmarks.py" compare --project-root "${ROOT_DIR}" "$@"
fi
exec python3 "${ROOT_DIR}/scripts/quality/run_real_device_smoke.py" "${COMMAND}" --project-root "${ROOT_DIR}" "$@"
