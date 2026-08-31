#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_real_device_acceptance_documentation.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-docs.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

bash "${VERIFIER}"
mkdir -p "${FIXTURE_ROOT}/docs/architecture"
cp "${ROOT_DIR}/docs/architecture/system-overview.md" "${FIXTURE_ROOT}/docs/architecture/"
cp "${ROOT_DIR}/docs/architecture/ci-quality-gates.md" "${FIXTURE_ROOT}/docs/architecture/"
cp "${ROOT_DIR}/docs/architecture/tech-stack.md" "${FIXTURE_ROOT}/docs/architecture/"
cp "${ROOT_DIR}/docs/architecture/roadmap-and-open-gaps.md" "${FIXTURE_ROOT}/docs/architecture/"
python3 - "${FIXTURE_ROOT}/docs/architecture/roadmap-and-open-gaps.md" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
path.write_text(path.read_text(encoding="utf-8").replace("startupProfileBenefit", "removedProfileVerdict"), encoding="utf-8")
PY
if REAL_DEVICE_ACCEPTANCE_DOC_ROOT="${FIXTURE_ROOT}" bash "${VERIFIER}" >"${FIXTURE_ROOT}/stale.log" 2>&1; then
  echo "[real-device-docs-test][FAIL] stale verdict documentation unexpectedly passed" >&2
  exit 1
fi
grep -Fq "roadmap-and-open-gaps.md documents the Profile verdict" "${FIXTURE_ROOT}/stale.log"

echo "[real-device-docs-test][PASS] aligned documents accepted and stale verdict fixture rejected."
