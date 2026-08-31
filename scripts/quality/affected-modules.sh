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
declare -a smoke_classes=("com.ytone.longcare.ExampleInstrumentedTest")

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

add_changed_android_test_class() {
  local file="$1"
  local relative

  case "${file}" in
    app/src/androidTest/kotlin/*)
      relative="${file#app/src/androidTest/kotlin/}"
      ;;
    app/src/androidTest/java/*)
      relative="${file#app/src/androidTest/java/}"
      ;;
    *)
      return 0
      ;;
  esac

  if ! git cat-file -e "${HEAD_REF}:${file}" 2>/dev/null; then
    return 0
  fi

  relative="${relative%.*}"
  add_smoke_class_unique "${relative//\//.}"
}

resolve_base_ref() {
  if [[ -n "${BASE_REF}" ]]; then
    echo "${BASE_REF}"
    return 0
  fi

  if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify "origin/${GITHUB_BASE_REF}^{commit}" >/dev/null 2>&1; then
    echo "origin/${GITHUB_BASE_REF}"
    return 0
  fi

  if git rev-parse --verify "origin/master^{commit}" >/dev/null 2>&1; then
    echo "origin/master"
    return 0
  fi

  if git rev-parse --verify "origin/main^{commit}" >/dev/null 2>&1; then
    echo "origin/main"
    return 0
  fi

  if git rev-parse --verify "HEAD~1^{commit}" >/dev/null 2>&1; then
    echo "HEAD~1"
    return 0
  fi

  echo "HEAD"
}

BASE_REF="$(resolve_base_ref)"

validate_commit_ref() {
  local label="$1"
  local ref="$2"

  if [[ -z "${ref}" ]] || ! git rev-parse --verify "${ref}^{commit}" >/dev/null 2>&1; then
    echo "Invalid ${label} ref '${ref}': expected a commit" >&2
    exit 1
  fi
}

validate_commit_ref "base" "${BASE_REF}"
validate_commit_ref "head" "${HEAD_REF}"

read_changed_files() {
  local range="$1"
  local output=""
  local line

  if ! output="$(git diff --name-only "${range}" 2>&1)"; then
    DIFF_ERROR="${output}"
    changed_files=()
    return 1
  fi

  changed_files=()
  if [[ -n "${output}" ]]; then
    while IFS= read -r line; do
      changed_files+=("${line}")
    done <<< "${output}"
  fi
  DIFF_ERROR=""
  return 0
}

DIFF_ERROR=""
DIFF_RANGE="${BASE_REF}...${HEAD_REF}"
if ! read_changed_files "${DIFF_RANGE}"; then
  three_dot_error="${DIFF_ERROR}"
  DIFF_RANGE="${BASE_REF}..${HEAD_REF}"
  if ! read_changed_files "${DIFF_RANGE}"; then
    two_dot_error="${DIFF_ERROR}"
    echo "Unable to diff valid affected refs '${BASE_REF}' and '${HEAD_REF}'." >&2
    echo "Three-dot diff failed: ${three_dot_error}" >&2
    echo "Two-dot diff failed: ${two_dot_error}" >&2
    exit 1
  fi
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
    app/src/main/*|baselineprofile/*)
      run_instrumentation="true"
      ;;
    app/src/androidTest/*)
      run_instrumentation="true"
      add_changed_android_test_class "${file}"
      ;;
  esac

  case "${file}" in
    .github/workflows/android-ci.yml|.github/scripts/run-instrumentation-smoke.sh|scripts/quality/affected-modules.sh)
      run_instrumentation="true"
      ;;
  esac

  case "${file}" in
    app/src/main/kotlin/com/ytone/longcare/features/service/*|app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/*)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest"
      ;;
  esac

  case "${file}" in
    app/src/main/kotlin/com/ytone/longcare/MainActivity.kt|app/src/main/kotlin/com/ytone/longcare/app/MainApplication.kt)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.smoke.MainActivitySmokeTest"
      ;;
  esac
done

if [[ "${full_scope}" == "true" ]]; then
  selected_modules=("${ALL_MODULES[@]}")
fi

if [[ "${#selected_modules[@]}" -eq 0 ]]; then
  add_unique ":app"
fi

affected_scope="partial"
verify_tasks=":app:lintDebug :app:assembleDebug"
if [[ "${full_scope}" == "true" ]]; then
  affected_scope="full"
  verify_tasks=":app:lintDebug :app:assembleDebug :app:bundleDebug"
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
