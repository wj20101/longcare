#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

bash "${ROOT_DIR}/scripts/quality/test_real_device_acceptance_config.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_preflight.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_acceptance_manifest.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_smoke_ledger.sh"
bash "${ROOT_DIR}/scripts/quality/test_startup_benchmark_round_comparator.sh"
bash "${ROOT_DIR}/scripts/quality/test_physical_startup_benchmark_runner.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_acceptance_report_privacy.sh"
bash "${ROOT_DIR}/scripts/quality/test_real_device_acceptance_documentation.sh"

echo "[real-device-acceptance-evidence-test][PASS] static contract, device/build ledger, smoke, redaction, two-round comparison, privacy, and documentation fixtures passed without a real device or Release build."
