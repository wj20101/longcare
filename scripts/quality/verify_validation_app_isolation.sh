#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
python3 "${ROOT_DIR}/scripts/quality/verify_validation_app_isolation.py" "${ROOT_DIR}" "${2:-}"
