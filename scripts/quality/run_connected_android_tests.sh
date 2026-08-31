#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

declare -a CONNECTED_TEST_TASKS=(
  ":app:connectedDebugAndroidTest"
  ":core:data:connectedDebugAndroidTest"
)

echo "[connected-tests] tasks=${CONNECTED_TEST_TASKS[*]}"
exec ./gradlew --no-daemon "${CONNECTED_TEST_TASKS[@]}" "$@"
