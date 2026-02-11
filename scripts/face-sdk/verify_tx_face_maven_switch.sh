#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

LIVE_COORD="${TX_FACE_LIVE_COORD:-com.tencent.cloud.huiyansdkface:wbcloudface-live:6.6.2-8e4718fc}"
NORMAL_COORD="${TX_FACE_NORMAL_COORD:-com.tencent.cloud.huiyansdkface:wbcloudface-normal:5.1.10-4e3e198}"

cd "${ROOT_DIR}"

echo "[1/3] Publish local Tencent face AARs to mavenLocal"
scripts/face-sdk/publish_tx_face_aars.sh --mode local

echo "[2/3] Run build checks with TX_FACE_SDK_SOURCE=maven"
./gradlew --no-daemon :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest :app:processReleaseMainManifest \
  -PTX_FACE_SDK_SOURCE=maven \
  -PTX_FACE_INCLUDE_MAVEN_LOCAL=true \
  -PTX_FACE_LIVE_COORD="${LIVE_COORD}" \
  -PTX_FACE_NORMAL_COORD="${NORMAL_COORD}"

echo "[3/3] Enforce repository quality gates"
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
bash scripts/quality/verify_cancellation_guards.sh app/src/main/kotlin
bash scripts/quality/verify_no_empty_catch_blocks.sh app/src/main/kotlin
bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts .github/workflows/android-ci.yml
bash scripts/quality/verify_release_exported_components.sh

echo "Tencent face SDK maven switch verification passed."
