#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OWNERS_FILE=""
GRADLE_COMMAND="${INSTRUMENTATION_GRADLE_COMMAND:-}"
GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --owners-file)
      OWNERS_FILE="${2:-}"
      shift 2
      ;;
    --gradle-command)
      GRADLE_COMMAND="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: run_connected_instrumentation_suite.sh [--project-root <path>] [--owners-file <path>] [--gradle-command <path>] [-- <Gradle args>]"
      exit 0
      ;;
    --)
      shift
      GRADLE_ARGS=("$@")
      break
      ;;
    *)
      echo "[connected-instrumentation-suite][FAIL] unknown argument: $1; pass Gradle arguments after --" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
OWNERS_FILE="${OWNERS_FILE:-${PROJECT_ROOT}/scripts/quality/instrumentation_test_modules.txt}"
GRADLE_COMMAND="${GRADLE_COMMAND:-${PROJECT_ROOT}/gradlew}"
[[ "${OWNERS_FILE}" == /* ]] || OWNERS_FILE="${PROJECT_ROOT}/${OWNERS_FILE}"
[[ "${GRADLE_COMMAND}" == /* ]] || GRADLE_COMMAND="${PROJECT_ROOT}/${GRADLE_COMMAND}"

bash "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify_instrumentation_test_ownership.sh" \
  --project-root "${PROJECT_ROOT}" \
  --owners-file "${OWNERS_FILE}" \
  --aggregate-script "${BASH_SOURCE[0]}"

if [[ ! -x "${GRADLE_COMMAND}" ]]; then
  echo "[connected-instrumentation-suite][FAIL] Gradle command is not executable: ${GRADLE_COMMAND}" >&2
  exit 1
fi

TASKS=()
while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
  module="${raw_line%%#*}"
  module="${module#"${module%%[![:space:]]*}"}"
  module="${module%"${module##*[![:space:]]}"}"
  [[ -n "${module}" ]] || continue
  TASKS+=("${module}:connectedDebugAndroidTest")
done < "${OWNERS_FILE}"

if [[ ${#TASKS[@]} -eq 0 ]]; then
  echo "[connected-instrumentation-suite][FAIL] no module-qualified connected tasks were produced from ${OWNERS_FILE}" >&2
  exit 1
fi

echo "[connected-instrumentation-suite] owners=${#TASKS[@]} serial=${ANDROID_SERIAL:-all-connected-devices}"
printf '[connected-instrumentation-suite] task=%s\n' "${TASKS[@]}"

if [[ ${#GRADLE_ARGS[@]} -gt 0 ]]; then
  "${GRADLE_COMMAND}" --no-daemon "${TASKS[@]}" "${GRADLE_ARGS[@]}"
else
  "${GRADLE_COMMAND}" --no-daemon "${TASKS[@]}"
fi
