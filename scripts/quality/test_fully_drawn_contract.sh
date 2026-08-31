#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_fully_drawn_contract.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-fully-drawn.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

FILES=(
  app/src/main/kotlin/com/ytone/longcare/navigation/StartupFullyDrawn.kt
  app/src/main/kotlin/com/ytone/longcare/navigation/PrivacyConsentDialog.kt
  app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt
  app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt
  feature/home/src/main/kotlin/com/ytone/longcare/features/home/api/HomeFeatureContract.kt
  app/src/androidTest/kotlin/com/ytone/longcare/navigation/StartupFullyDrawnInstrumentationTest.kt
)

make_fixture() {
  local fixture="$1"
  local file
  for file in "${FILES[@]}"; do
    mkdir -p "${fixture}/$(dirname "${file}")"
    cp "${ROOT_DIR}/${file}" "${fixture}/${file}"
  done
}

remove_marker() {
  local file="$1"
  local marker="$2"
  python3 - "${file}" "${marker}" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
marker = sys.argv[2]
source = path.read_text(encoding="utf-8")
if source.count(marker) != 1:
    raise SystemExit(f"expected one marker before mutation: {marker}")
path.write_text(source.replace(marker, "fully-drawn-root-removed", 1), encoding="utf-8")
PY
}

VALID="${FIXTURE_ROOT}/valid"
make_fixture "${VALID}"
python3 "${VERIFIER}" --project-root "${VALID}"

expect_missing_root() {
  local scenario="$1"
  local file="$2"
  local marker="$3"
  local fixture="${FIXTURE_ROOT}/${scenario}"
  make_fixture "${fixture}"
  remove_marker "${fixture}/${file}" "${marker}"
  local output="${FIXTURE_ROOT}/${scenario}.log"
  if python3 "${VERIFIER}" --project-root "${fixture}" >"${output}" 2>&1; then
    echo "[fully-drawn-test][FAIL] ${scenario} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "missing fully-drawn root for scenario ${scenario}" "${output}" || {
    echo "[fully-drawn-test][FAIL] ${scenario} failure was not scenario-specific" >&2
    sed -n '1,120p' "${output}" >&2
    exit 1
  }
  echo "[fully-drawn-test][PASS] ${scenario} missing report point rejected"
}

expect_missing_root \
  first_run_privacy \
  app/src/main/kotlin/com/ytone/longcare/navigation/PrivacyConsentDialog.kt \
  'expectedRoot = StartupRoot.Privacy'
expect_missing_root \
  logged_out \
  app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt \
  'expectedRoot = StartupRoot.Login'
expect_missing_root \
  care_home \
  app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt \
  'HomeExperience.Care -> StartupRoot.CareHome'
expect_missing_root \
  sales_home \
  app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt \
  'HomeExperience.Sales -> StartupRoot.SalesHome'

HOME_BRIDGE_FIXTURE="${FIXTURE_ROOT}/home_experience_bridge"
make_fixture "${HOME_BRIDGE_FIXTURE}"
remove_marker \
  "${HOME_BRIDGE_FIXTURE}/feature/home/src/main/kotlin/com/ytone/longcare/features/home/api/HomeFeatureContract.kt" \
  'startupReporter(uiState.experience)'
HOME_BRIDGE_OUTPUT="${FIXTURE_ROOT}/home_experience_bridge.log"
if python3 "${VERIFIER}" --project-root "${HOME_BRIDGE_FIXTURE}" >"${HOME_BRIDGE_OUTPUT}" 2>&1; then
  echo "[fully-drawn-test][FAIL] missing Home experience callback unexpectedly passed" >&2
  exit 1
fi
grep -Fq -- "Home feature must report each resolved experience" "${HOME_BRIDGE_OUTPUT}" || {
  echo "[fully-drawn-test][FAIL] Home experience callback failure was not specific" >&2
  sed -n '1,120p' "${HOME_BRIDGE_OUTPUT}" >&2
  exit 1
}
echo "[fully-drawn-test][PASS] missing Home experience callback rejected"

echo "[fully-drawn-test][PASS] all four Startup roots and the Home callback bridge have negative fixtures."
