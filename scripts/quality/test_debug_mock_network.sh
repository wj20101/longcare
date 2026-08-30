#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFIER="${ROOT_DIR}/scripts/quality/verify_debug_mock_network.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-debug-mock-network.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

python3 "${VERIFIER}" --project-root "${ROOT_DIR}"

python3 - "${ROOT_DIR}" "${FIXTURE_ROOT}/base" <<'PY'
import shutil
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
required = (
    "app/build.gradle.kts",
    "gradle.properties",
    "app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockInterceptor.kt",
    "app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockRouteRegistry.kt",
    "app/src/debug/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
    "app/src/release/kotlin/com/ytone/longcare/di/PhotoCloudUploadModule.kt",
    "app/src/test/kotlin/com/ytone/longcare/network/interceptor/MockInterceptorTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/network/interceptor/MockFixtureContractTest.kt",
    "app/src/testDebug/kotlin/com/ytone/longcare/di/DebugPhotoCloudUploadModuleTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/di/AppFlavorInterceptorApplierTest.kt",
    "app/src/test/kotlin/com/ytone/longcare/worker/UpdateWorkerTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/navigation/IdentificationPostVerificationNavigationTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/features/update/ui/AppUpdatePromptTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/di/DebugPhotoCloudUploaderDeviceTest.kt",
    "app/src/androidTest/kotlin/com/ytone/longcare/platform/webview/WebViewEntryInstrumentationTest.kt",
    "feature/location/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeOfflineTest.kt",
    "app/src/debug/assets/mock/start_config.json",
    "README.md",
    "docs/README.md",
    "docs/architecture/tech-stack.md",
    "docs/architecture/system-overview.md",
    "docs/architecture/ci-quality-gates.md",
    "docs/architecture/roadmap-and-open-gaps.md",
)
for relative in required:
    destination = target / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / relative, destination)
PY

expect_failure() {
  local name="$1"
  local expected="$2"
  shift 2
  local output="${FIXTURE_ROOT}/${name}.log"
  if python3 "${VERIFIER}" --project-root "${FIXTURE_ROOT}/${name}" "$@" >"${output}" 2>&1; then
    echo "[debug-mock-network-test][FAIL] ${name} unexpectedly passed" >&2
    exit 1
  fi
  if ! grep -Fq -- "${expected}" "${output}"; then
    echo "[debug-mock-network-test][FAIL] ${name} did not report: ${expected}" >&2
    sed -n '1,180p' "${output}" >&2
    exit 1
  fi
  echo "[debug-mock-network-test][PASS] ${name} rejected"
}

cp -R "${FIXTURE_ROOT}/base" "${FIXTURE_ROOT}/default-enabled"
python3 - "${FIXTURE_ROOT}/default-enabled/app/build.gradle.kts" <<'PY'
import sys
import re
from pathlib import Path
path = Path(sys.argv[1])
content = path.read_text()
content = re.sub(
    r'(gradleProperty\("debug\.useMockData"\)\s*\.orElse\()"false"(\))',
    r'\1"true"\2',
    content,
    count=1,
)
path.write_text(content)
PY
expect_failure "default-enabled" "fallback must be false"

cp -R "${FIXTURE_ROOT}/base" "${FIXTURE_ROOT}/release-leak"
mkdir -p \
  "${FIXTURE_ROOT}/release-leak/app/src/release/kotlin/com/ytone/longcare/network/interceptor"
cp \
  "${FIXTURE_ROOT}/base/app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockInterceptor.kt" \
  "${FIXTURE_ROOT}/release-leak/app/src/release/kotlin/com/ytone/longcare/network/interceptor/MockInterceptor.kt"
expect_failure "release-leak" "Debug-only marker leaked into production source"

cp -R "${FIXTURE_ROOT}/base" "${FIXTURE_ROOT}/release-build-config"
mkdir -p "${FIXTURE_ROOT}/release-build-config/generated"
printf '%s\n' 'public static final boolean USE_MOCK_DATA = true;' \
  > "${FIXTURE_ROOT}/release-build-config/generated/BuildConfig.java"
expect_failure \
  "release-build-config" \
  "generated Release BuildConfig must contain USE_MOCK_DATA = false" \
  --release-build-config generated/BuildConfig.java

cp -R "${FIXTURE_ROOT}/base" "${FIXTURE_ROOT}/stale-documentation"
python3 - "${FIXTURE_ROOT}/stale-documentation/README.md" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
path.write_text(
    path.read_text().replace(
        "未知第一方 method/path 会在本地 fail-closed",
        "未知第一方 method/path 会透传",
        1,
    )
)
PY
expect_failure "stale-documentation" "Debug Mock documentation is stale in README.md"

echo "[debug-mock-network-test][PASS] valid source/documentation and default, Release leak, generated BuildConfig, and stale documentation negative fixtures behaved correctly."
