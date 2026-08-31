#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFLIGHT="${ROOT_DIR}/scripts/quality/preflight_real_device.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-real-device-preflight.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

make_fixture() {
  local mutation="$1"
  local destination="$2"
  python3 - "${destination}" "${mutation}" <<'PY'
import json
import sys
from pathlib import Path

destination, mutation = map(Path, sys.argv[1:])
serial = "fixture-api36-physical"
data = {
    "selectedSerial": serial,
    "connectedSerials": [serial],
    "properties": {
        "ro.kernel.qemu": "0",
        "ro.build.version.sdk": "36",
        "ro.product.cpu.abi": "arm64-v8a",
        "ro.build.fingerprint": "google/fixture/fixture:16/ABC/123:user/release-keys",
        "ro.product.model": "Fixture Phone",
    },
    "cpuPresent": "0-7",
    "cpuInfo": "",
    "batteryDump": "AC powered: false\nUSB powered: false\nWireless powered: false\nstatus: 3\nlevel: 86\n",
    "thermalDump": "Thermal Status: 0\n",
}

if mutation.name == "valid-aosp":
    pass
elif mutation.name == "valid-oem":
    data["batteryDump"] = "Battery{mLevel=82,mStatus=3,mAcOnline=false,mUsbOnline=false,mWirelessOnline=false}"
    data["thermalDump"] = "ThermalManager{mStatus=1}"
elif mutation.name == "emulator":
    data["properties"]["ro.kernel.qemu"] = "1"
elif mutation.name == "api28":
    data["properties"]["ro.build.version.sdk"] = "28"
elif mutation.name == "x86":
    data["properties"]["ro.product.cpu.abi"] = "x86_64"
elif mutation.name == "single-core":
    data["cpuPresent"] = "0"
elif mutation.name == "unselected-multiple":
    data["selectedSerial"] = ""
    data["connectedSerials"] = [serial, "second-device"]
elif mutation.name == "low-battery":
    data["batteryDump"] = data["batteryDump"].replace("level: 86", "level: 20")
elif mutation.name == "charging":
    data["batteryDump"] = data["batteryDump"].replace("USB powered: false", "USB powered: true").replace("status: 3", "status: 2")
elif mutation.name == "hot":
    data["thermalDump"] = "Thermal Status: 3\n"
elif mutation.name == "unknown-status":
    data["batteryDump"] = "vendor battery output unavailable"
    data["thermalDump"] = "vendor thermal output unavailable"
else:
    raise SystemExit(f"unknown mutation: {mutation.name}")

destination.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
PY
}

run_valid() {
  local mutation="$1"
  local fixture="${FIXTURE_ROOT}/${mutation}.json"
  local report="${FIXTURE_ROOT}/${mutation}-report.json"
  local log="${FIXTURE_ROOT}/${mutation}.log"
  make_fixture "${mutation}" "${fixture}"
  python3 "${PREFLIGHT}" --snapshot "${fixture}" --output "${report}" 2>"${log}"
  python3 - "${fixture}" "${report}" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

fixture, report = map(Path, sys.argv[1:])
source = json.loads(fixture.read_text(encoding="utf-8"))
result = json.loads(report.read_text(encoding="utf-8"))
expected = hashlib.sha256(source["selectedSerial"].encode()).hexdigest()
assert result["status"] == "qualified"
assert result["deviceIdHash"] == expected
assert source["selectedSerial"] not in report.read_text(encoding="utf-8")
PY
}

assert_rejected() {
  local mutation="$1"
  local expected="$2"
  local fixture="${FIXTURE_ROOT}/${mutation}.json"
  local output="${FIXTURE_ROOT}/${mutation}.out"
  make_fixture "${mutation}" "${fixture}"
  if python3 "${PREFLIGHT}" --snapshot "${fixture}" >"${output}" 2>&1; then
    echo "[real-device-preflight-test][FAIL] ${mutation} unexpectedly passed" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output}"; then
    echo "[real-device-preflight-test][FAIL] ${mutation} did not report ${expected}" >&2
    sed 's/^/[fixture-output] /' "${output}" >&2
    exit 1
  fi
}

run_valid "valid-aosp"
run_valid "valid-oem"
assert_rejected "emulator" "emulator/qemu devices cannot provide physical acceptance evidence"
assert_rejected "api28" "API 29 or earlier cannot guarantee TTFD evidence"
assert_rejected "x86" "primaryAbi: expected arm64-v8a"
assert_rejected "single-core" "cpuCores: expected >= 2"
assert_rejected "unselected-multiple" "ANDROID_SERIAL is required"
assert_rejected "low-battery" "battery.level: expected >= 50%"
assert_rejected "charging" "device must be unplugged and not charging"
assert_rejected "hot" "thermalStatus: expected <= 1"
assert_rejected "unknown-status" "unable to parse"

echo "[real-device-preflight-test][PASS] AOSP/OEM physical fixtures qualified and nine fail-closed device fixtures rejected."
