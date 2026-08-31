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
  ":feature:location"
  ":feature:photoupload"
  ":feature:servicecountdown"
)

declare -a selected_modules=()
declare -a changed_files=()
full_scope="false"
run_instrumentation="false"
run_home_feature_instrumentation="false"
run_login_feature_instrumentation="false"
MATRIX_FILE="${ROOT_DIR}/scripts/quality/target_platform_test_matrix.properties"
# shellcheck source=scripts/quality/target_readiness_values.sh
source "${ROOT_DIR}/scripts/quality/target_readiness_values.sh"
current_smoke_classes="$(read_target_readiness_value "${MATRIX_FILE}" current_target_smoke_classes)"
current_home_feature_smoke_classes="$(
  read_target_readiness_value "${MATRIX_FILE}" current_target_home_feature_smoke_classes
)"
current_login_feature_smoke_classes="$(
  read_target_readiness_value "${MATRIX_FILE}" current_target_login_feature_smoke_classes
)"
IFS=',' read -r -a smoke_classes <<< "${current_smoke_classes}"
IFS=',' read -r -a home_feature_smoke_classes <<< "${current_home_feature_smoke_classes}"
IFS=',' read -r -a login_feature_smoke_classes <<< "${current_login_feature_smoke_classes}"

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
if [[ -n "${AFFECTED_MODULES_CHANGED_FILES:-}" ]]; then
  changed_files=()
  while IFS= read -r line; do
    [[ -n "${line}" ]] && changed_files+=("${line}")
  done <<< "${AFFECTED_MODULES_CHANGED_FILES}"
elif [[ "${#changed_files[@]}" -eq 0 ]] && ! git diff --quiet "${DIFF_RANGE}" 2>/dev/null; then
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
    feature/location/*) add_unique ":feature:location" ;;
    feature/photoupload/*) add_unique ":feature:photoupload" ;;
    feature/servicecountdown/*) add_unique ":feature:servicecountdown" ;;
  esac

  case "${file}" in
    core/domain/src/main/kotlin/com/ytone/longcare/domain/system/CompanyNameProvider.kt|core/data/src/main/kotlin/com/ytone/longcare/common/utils/SystemConfigManager.kt|core/data/src/main/kotlin/com/ytone/longcare/di/SystemConfigProviderModule.kt|core/data/src/test/kotlin/com/ytone/longcare/common/utils/SystemConfigManagerUserScopeTest.kt)
      add_unique ":core:domain"
      add_unique ":core:data"
      add_unique ":feature:home"
      ;;
  esac

  case "${file}" in
    app/src/main/*|app/src/androidTest/*|baselineprofile/*)
      run_instrumentation="true"
      ;;
  esac

  case "${file}" in
    feature/login/src/*)
      run_login_feature_instrumentation="true"
      ;;
  esac

  case "${file}" in
    feature/home/src/main/*|feature/home/src/androidTest/*)
      run_home_feature_instrumentation="true"
      ;;
  esac

  case "${file}" in
    app/src/main/kotlin/com/ytone/longcare/features/service/*|app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/*)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.features.countdown.manager.CountdownAlarmPermissionFallbackIntegrationTest"
      ;;
  esac

  case "${file}" in
    app/src/main/kotlin/com/ytone/longcare/MainActivity.kt|app/src/main/kotlin/com/ytone/longcare/app/MainApplication.kt)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.smoke.MainActivitySmokeTest"
      ;;
  esac

  case "${file}" in
    app/src/main/kotlin/com/ytone/longcare/navigation/*|app/src/main/kotlin/com/ytone/longcare/features/sales/SalesExperienceScreen.kt|app/src/main/kotlin/com/ytone/longcare/presentation/sales/SalesNavigationState.kt|app/src/androidTest/kotlin/com/ytone/longcare/navigation/*|app/src/androidTest/kotlin/com/ytone/longcare/features/sales/SalesNavigationStateRestorationTest.kt)
      run_instrumentation="true"
      add_smoke_class_unique "com.ytone.longcare.navigation.EntryNavigationInstrumentationTest"
      add_smoke_class_unique "com.ytone.longcare.navigation.HomeGraphOwnerInstrumentationTest"
      add_smoke_class_unique "com.ytone.longcare.features.sales.SalesNavigationStateRestorationTest"
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

for module in "${selected_modules[@]}"; do
  if [[ "${module}" == ":feature:home" ]]; then
    verify_tasks+=" :feature:home:compileDebugKotlin :feature:home:testDebugUnitTest :feature:home:lintDebug :feature:home:compileDebugAndroidTestKotlin"
  fi
  if [[ "${module}" == ":feature:login" ]]; then
    verify_tasks+=" :feature:login:compileDebugKotlin :feature:login:testDebugUnitTest :feature:login:lintDebug :feature:login:compileDebugAndroidTestKotlin"
  fi
done

if [[ "${run_instrumentation}" != "true" ]]; then
  run_instrumentation="false"
fi
if [[ "${run_home_feature_instrumentation}" != "true" ]]; then
  run_home_feature_instrumentation="false"
fi
if [[ "${run_login_feature_instrumentation}" != "true" ]]; then
  run_login_feature_instrumentation="false"
fi

modules_csv="$(IFS=,; echo "${selected_modules[*]}")"
smoke_classes_csv="$(IFS=,; echo "${smoke_classes[*]}")"
home_feature_smoke_classes_csv="$(IFS=,; echo "${home_feature_smoke_classes[*]}")"
login_feature_smoke_classes_csv="$(IFS=,; echo "${login_feature_smoke_classes[*]}")"
changed_files_count="${#changed_files[@]}"

case "${FORMAT}" in
  github)
    echo "affected_scope=${affected_scope}"
    echo "affected_modules=${modules_csv}"
    echo "verify_tasks=${verify_tasks}"
    echo "run_instrumentation=${run_instrumentation}"
    echo "smoke_test_classes=${smoke_classes_csv}"
    echo "run_home_feature_instrumentation=${run_home_feature_instrumentation}"
    echo "home_feature_smoke_test_classes=${home_feature_smoke_classes_csv}"
    echo "run_login_feature_instrumentation=${run_login_feature_instrumentation}"
    echo "login_feature_smoke_test_classes=${login_feature_smoke_classes_csv}"
    echo "changed_files_count=${changed_files_count}"
    ;;
  text)
    echo "affected_scope=${affected_scope}"
    echo "affected_modules=${modules_csv}"
    echo "verify_tasks=${verify_tasks}"
    echo "run_instrumentation=${run_instrumentation}"
    echo "smoke_test_classes=${smoke_classes_csv}"
    echo "run_home_feature_instrumentation=${run_home_feature_instrumentation}"
    echo "home_feature_smoke_test_classes=${home_feature_smoke_classes_csv}"
    echo "run_login_feature_instrumentation=${run_login_feature_instrumentation}"
    echo "login_feature_smoke_test_classes=${login_feature_smoke_classes_csv}"
    echo "changed_files_count=${changed_files_count}"
    ;;
  *)
    echo "Unsupported format: ${FORMAT}" >&2
    exit 1
    ;;
esac
