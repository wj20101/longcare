#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VARIANT="${1:-debug}"
if [[ $# -gt 1 ]]; then
  echo "Usage: bash scripts/release/build-dual-apks.sh [debug|release]" >&2
  exit 2
fi
case "${VARIANT}" in
  debug|--debug)
    VARIANT="debug"
    TASK_VARIANT="Debug"
    FLAGS=(-Pdebug.useMockData=false)
    ;;
  release|--release)
    VARIANT="release"
    TASK_VARIANT="Release"
    FLAGS=(-PLONGCARE_ALLOW_UNSIGNED_RELEASE=false -PALLOW_UNSIGNED_RELEASE=false)
    ;;
  *)
    echo "Only debug and release are supported; release requires a valid signing configuration." >&2
    exit 2
    ;;
esac
cd "${ROOT_DIR}"
# Retire only this script's previous export before building: failure cannot look like a fresh pair.
OUTPUT_DIR="${ROOT_DIR}/build/outputs/dual-apk/${VARIANT}"
if [[ -d "${OUTPUT_DIR}" ]]; then
  ARCHIVE_DIR="$(mktemp -d "${ROOT_DIR}/build/outputs/dual-apk/.${VARIANT}-previous.XXXXXX")"
  mv "${OUTPUT_DIR}" "${ARCHIVE_DIR}/export"
fi
./gradlew --no-daemon ":app:assemble${TASK_VARIANT}" ":assistant:assemble${TASK_VARIANT}" "${FLAGS[@]}"
bash scripts/quality/verify_validation_app_isolation.sh "${ROOT_DIR}" "${VARIANT}"
python3 scripts/release/package_dual_apks.py "${ROOT_DIR}" "${VARIANT}"
