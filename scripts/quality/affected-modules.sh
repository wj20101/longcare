#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

FORMAT="text"
BASE_REF="${BASE_REF:-}"
HEAD_REF="${HEAD_REF:-HEAD}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --format)
      FORMAT="${2:-text}"
      shift 2
      ;;
    --base)
      BASE_REF="${2:-}"
      shift 2
      ;;
    --head)
      HEAD_REF="${2:-HEAD}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

declare -a ALL_MODULES=(
  ":app"
  ":baselineprofile"
  ":core:model"
  ":core:domain"
  ":core:data"
  ":core:ui"
  ":core:common"
  ":feature:login"
  ":feature:home"
  ":feature:identification"
)

declare -a selected_modules=()
declare -a changed_files=()
full_scope="false"
run_instrumentation="false"
declare -a smoke_classes=("com.ytone.longcare.smoke.MainActivitySmokeTest")

add_unique() {
  local value="$1"
  local current
  for current in "${selected_modules[@]:-}"; do
    if [[ "${current}" == "${value}" ]]; then
      return 0
    fi
  done
  selected_modules+=("${value}")
}

add_smoke_class_unique() {
  local value="$1"
  local current
  for current in "${smoke_classes[@]:-}"; do
    if [[ "${current}" == "${value}" ]]; then
      return 0
    fi
  done
  smoke_classes+=("${value}")
}

resolve_base_ref() {
  if [[ -n "${BASE_REF}" ]]; then
    echo "${BASE_REF}"
    return 0
  fi

  if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    echo "origin/${GITHUB_BASE_REF}"
    return 0
  fi

  if git rev-parse --verify origin/master >/dev/null 2>&1; then
    echo "origin/master"
    return 0
  fi

  if git rev-parse --verify origin/main >/dev/null 2>&1; then
    echo "origin/main"
    return 0
  fi

  if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    echo "HEAD~1"
    return 0
  fi

  echo "HEAD"
}

BASE_REF="$(resolve_base_ref)"
DIFF_RANGE="${BASE_REF}...${HEAD_REF}"

read_changed_files() {
  local range="$1"
  local line
  changed_files=()
  while IFS= read -r line; do
    changed_files+=("${line}")
  done < <(git diff --name-only "${range}" 2>/dev/null || true)
}

read_changed_files "${DIFF_RANGE}"
if [[ "${#changed_files[@]}" -eq 0 ]] && ! git diff --quiet "${DIFF_RANGE}" 2>/dev/null; then
  DIFF_RANGE="${BASE_REF}..${HEAD_REF}"
  read_changed_files "${DIFF_RANGE}"
fi

for file in "${changed_files[@]:-}"; do
  [[ -z "${file}" ]] && continue

  case "${file}" in
    settings.gradle.kts|build.gradle.kts|constants.gradle.kts|gradle.properties|gradle/*|build-logic/*|.github/workflows/*|scripts/quality/*)
      full_scope="true"
      ;;
  esac

  case "${file}" in
    app/*) add_unique ":app" ;;
    baselineprofile/*) add_unique ":baselineprofile" ;;
    core/model/*) add_unique ":core:model" ;;
    core/domain/*) add_unique ":core:domain" ;;
    core/data/*) add_unique ":core:data" ;;
    core/ui/*) add_unique ":core:ui" ;;
    core/common/*) add_unique ":core:common" ;;
    feature/login/*) add_unique ":feature:login" ;;
    feature/home/*) add_unique ":feature:home" ;;
    feature/identification/*) add_unique ":feature:identification" ;;
  esac

  case "${file}" in
    app/src/androidTest/*|baselineprofile/*|app/src/main/kotlin/com/ytone/longcare/MainActivity.kt|app/src/main/kotlin/com/ytone/longcare/features/service/*|app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/*)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest"
      ;;
  esac
done

if [[ "${full_scope}" == "true" ]]; then
  selected_modules=("${ALL_MODULES[@]}")
  run_instrumentation="true"
  add_smoke_class_unique "com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest"
fi

if [[ "${#selected_modules[@]}" -eq 0 ]]; then
  add_unique ":app"
fi

affected_scope="partial"
verify_tasks=":app:lintDebug :app:testDebugUnitTest :app:assembleDebug"
if [[ "${full_scope}" == "true" ]]; then
  affected_scope="full"
  verify_tasks=":app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:bundleDebug :baselineprofile:assemble :app:bundleRelease"
fi

if [[ "${run_instrumentation}" != "true" ]]; then
  run_instrumentation="false"
fi

modules_csv="$(IFS=,; echo "${selected_modules[*]}")"
smoke_classes_csv="$(IFS=,; echo "${smoke_classes[*]}")"
changed_files_count="${#changed_files[@]}"

case "${FORMAT}" in
  github)
    echo "affected_scope=${affected_scope}"
    echo "affected_modules=${modules_csv}"
    echo "verify_tasks=${verify_tasks}"
    echo "run_instrumentation=${run_instrumentation}"
    echo "smoke_test_classes=${smoke_classes_csv}"
    echo "changed_files_count=${changed_files_count}"
    ;;
  text)
    echo "affected_scope=${affected_scope}"
    echo "affected_modules=${modules_csv}"
    echo "verify_tasks=${verify_tasks}"
    echo "run_instrumentation=${run_instrumentation}"
    echo "smoke_test_classes=${smoke_classes_csv}"
    echo "changed_files_count=${changed_files_count}"
    ;;
  *)
    echo "Unsupported format: ${FORMAT}" >&2
    exit 1
    ;;
esac
