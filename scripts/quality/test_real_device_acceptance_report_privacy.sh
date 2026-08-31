#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_real_device_acceptance_reports.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-privacy.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT
PROJECT="${FIXTURE_ROOT}/project"
REPORT_ROOT="${PROJECT}/build/reports/real-device-acceptance/privacy-fixture"
mkdir -p "${REPORT_ROOT}"

python3 - "${REPORT_ROOT}/clean.json" <<'PY'
import json
import sys
from pathlib import Path
Path(sys.argv[1]).write_text(
    json.dumps({
        "deviceIdHash": "a" * 64,
        "prerequisites": ["care-account", "qlz-runtime-token"],
        "message": "token=<redacted> phone=<redacted-phone>",
        "url": "https://example.test/path?<redacted-query>",
    }, indent=2) + "\n",
    encoding="utf-8",
)
PY
python3 "${VERIFIER}" --project-root "${PROJECT}" --report-root "${REPORT_ROOT}" --serial fixture-raw-serial

expect_failure() {
  local name="$1"
  local payload="$2"
  local expected="$3"
  local fixture="${REPORT_ROOT}/${name}.json"
  python3 - "${fixture}" "${payload}" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[1]).write_text(sys.argv[2] + "\n", encoding="utf-8")
PY
  local output="${FIXTURE_ROOT}/${name}.log"
  if python3 "${VERIFIER}" --project-root "${PROJECT}" --report-root "${REPORT_ROOT}" \
    --serial fixture-raw-serial >"${output}" 2>&1; then
    echo "[real-device-report-privacy-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  grep -Fq -- "${expected}" "${output}" || {
    echo "[real-device-report-privacy-test][FAIL] ${name} did not report ${expected}" >&2
    sed 's/^/[fixture-output] /' "${output}" >&2
    exit 1
  }
  python3 - "${fixture}" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[1]).unlink()
PY
}

expect_failure "token" 'token=top-secret' "credential-or-account-value"
expect_failure "account" 'account=alice' "credential-or-account-value"
expect_failure "verification" 'verification_code=123456' "credential-or-account-value"
expect_failure "phone" 'contact 13800138000' "phone-number"
expect_failure "identity" '11010519491231002X' "identity-card"
expect_failure "url" 'https://example.test/path?token=secret' "full-url-query"
expect_failure "serial" 'fixture-raw-serial' "explicit raw serial"
expect_failure "emulator" 'emulator-5554' "emulator-serial"
expect_failure "host-path" '/Users/example/private/report.json' "host-absolute-path"

python3 - "${REPORT_ROOT}/photo.jpg" <<'PY'
import sys
from pathlib import Path
Path(sys.argv[1]).write_bytes(b"not-a-real-photo")
PY
if python3 "${VERIFIER}" --project-root "${PROJECT}" --report-root "${REPORT_ROOT}" >"${FIXTURE_ROOT}/photo.log" 2>&1; then
  echo "[real-device-report-privacy-test][FAIL] photo unexpectedly passed" >&2
  exit 1
fi
grep -Fq "photo/image artifact is forbidden" "${FIXTURE_ROOT}/photo.log"

echo "[real-device-report-privacy-test][PASS] sanitized report accepted and credentials, PII, URL, serial, host path, and photo fixtures rejected."
