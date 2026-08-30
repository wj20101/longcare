#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."

usage() {
  cat <<'USAGE'
Usage: verify_android_build_baseline.sh [--project-root <path>]

Verifies the repository-owned Android SDK/JDK/application version baseline.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[android-build-baseline][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
SETTINGS_FILE="${PROJECT_ROOT}/settings.gradle.kts"
CONSTANTS_FILE="${PROJECT_ROOT}/constants.gradle.kts"
CATALOG_FILE="${PROJECT_ROOT}/gradle/libs.versions.toml"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/quality/android_build_values.sh
source "${SCRIPT_DIR}/android_build_values.sh"
# shellcheck source=scripts/quality/gradle_constants.sh
source "${SCRIPT_DIR}/gradle_constants.sh"

ERRORS=()

fail() {
  ERRORS+=("$1")
}

require_file() {
  local path="$1"
  if [[ ! -f "${path}" ]]; then
    fail "required file is missing: ${path#"${PROJECT_ROOT}/"}"
    return 1
  fi
}

require_module_token() {
  local build_file="$1"
  local token="$2"
  local label="$3"
  [[ -f "${build_file}" ]] || return 0
  if ! grep -Fq -- "${token}" "${build_file}"; then
    fail "${build_file#"${PROJECT_ROOT}/"} missing required ${label}: ${token}"
  fi
}

forbid_module_token() {
  local build_file="$1"
  local token="$2"
  local label="$3"
  [[ -f "${build_file}" ]] || return 0
  if grep -Fq -- "${token}" "${build_file}"; then
    fail "${build_file#"${PROJECT_ROOT}/"} contains redundant ${label}: ${token}"
  fi
}

require_file "${SETTINGS_FILE}" || true
require_file "${CONSTANTS_FILE}" || true
require_file "${CATALOG_FILE}" || true

compile_sdk=""
target_sdk=""
min_sdk=""
agp_version=""
settings_plugin_version=""
jdk_version=""
app_version_code=""
app_version_name=""

if [[ -f "${SETTINGS_FILE}" ]]; then
  compile_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "compileSdk")"
  target_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "targetSdk")"
  min_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "minSdk")"
  settings_plugin_version="$(read_android_settings_plugin_version "${SETTINGS_FILE}")"

  if ! grep -Eq '^[[:space:]]*id\("com\.android\.settings"\)[[:space:]]*$' "${SETTINGS_FILE}"; then
    fail "com.android.settings is not applied in settings.gradle.kts"
  fi
fi

if [[ -f "${CATALOG_FILE}" ]]; then
  agp_version="$(read_toml_version "${CATALOG_FILE}" "agp")"
fi

if [[ -f "${CONSTANTS_FILE}" ]]; then
  jdk_version="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appJdkVersion")"
  app_version_code="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appVersionCode")"
  app_version_name="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appVersionName")"
fi

for sdk_field in compile_sdk target_sdk min_sdk; do
  sdk_value="${!sdk_field}"
  if [[ ! "${sdk_value}" =~ ^[0-9]+$ ]]; then
    fail "missing or invalid ${sdk_field//_/-} release value in settings.gradle.kts"
  fi
done

if [[ "${min_sdk}" =~ ^[0-9]+$ && "${target_sdk}" =~ ^[0-9]+$ && "${compile_sdk}" =~ ^[0-9]+$ ]]; then
  if (( min_sdk > target_sdk || target_sdk > compile_sdk )); then
    fail "invalid SDK order: minSdk(${min_sdk}) <= targetSdk(${target_sdk}) <= compileSdk(${compile_sdk}) is required"
  fi
  [[ "${min_sdk}" == "24" ]] || fail "unapproved minSdk=${min_sdk}; expected 24"
  [[ "${target_sdk}" == "36" ]] || fail "unapproved targetSdk=${target_sdk}; expected 36"
  [[ "${compile_sdk}" == "37" ]] || fail "unapproved compileSdk=${compile_sdk}; expected 37"
fi

[[ -n "${agp_version}" ]] || fail "missing agp version alias in gradle/libs.versions.toml"
[[ -n "${settings_plugin_version}" ]] || fail "missing com.android.settings plugin version declaration"
if [[ -n "${agp_version}" && -n "${settings_plugin_version}" && "${agp_version}" != "${settings_plugin_version}" ]]; then
  fail "plugin version mismatch: AGP=${agp_version}, com.android.settings=${settings_plugin_version}"
fi

[[ "${jdk_version}" =~ ^[0-9]+$ ]] || fail "missing or invalid appJdkVersion in constants.gradle.kts"
[[ "${jdk_version}" == "21" ]] || fail "unapproved JDK=${jdk_version}; expected 21"
[[ "${app_version_code}" =~ ^[0-9]+$ ]] || fail "missing or invalid appVersionCode in constants.gradle.kts"
[[ -n "${app_version_name}" ]] || fail "missing appVersionName in constants.gradle.kts"

if [[ -f "${CONSTANTS_FILE}" ]] && grep -En 'app(Compile|Target|Min)SdkVersion' "${CONSTANTS_FILE}" >/dev/null; then
  fail "legacy SDK extra remains in constants.gradle.kts; settings.gradle.kts must be the only SDK source"
fi

while IFS= read -r build_file; do
  relative_path="${build_file#"${PROJECT_ROOT}/"}"
  if grep -En '(^|[^[:alnum:]_])(compileSdk|minSdk|targetSdk)[[:space:]]*=' "${build_file}" >/dev/null; then
    fail "module-level SDK override found in ${relative_path}"
  fi
  if grep -En 'jvmToolchain\([[:space:]]*[0-9]+|JavaVersion\.toVersion\([[:space:]]*[0-9]+' "${build_file}" >/dev/null; then
    fail "numeric JDK override found in ${relative_path}; use appJdkVersion"
  fi
done < <(
  find "${PROJECT_ROOT}" \
    \( -path "${PROJECT_ROOT}/.git" -o -path "${PROJECT_ROOT}/.gradle" -o -path "${PROJECT_ROOT}/.worktrees" -o -path '*/build' -o -path "${PROJECT_ROOT}/openspec" \) -prune -o \
    -type f -name 'build.gradle.kts' -print
)

LOCATION_BUILD="${PROJECT_ROOT}/feature/location/build.gradle.kts"
for token in \
  'alias(libs.plugins.kotlin.compose)' \
  'compose = true' \
  'implementation(platform(libs.compose.bom))' \
  'implementation(libs.compose.ui)' \
  'implementation(libs.compose.material3)' \
  'implementation(libs.androidx.activity.compose)' \
  'implementation(libs.androidx.lifecycle.runtime.compose)' \
  'implementation(libs.hilt.navigation.compose)' \
  'implementation(libs.crashreport)'; do
  forbid_module_token "${LOCATION_BUILD}" "${token}" "location build capability/dependency"
done
require_module_token "${LOCATION_BUILD}" 'implementation(libs.androidx.core.ktx)' "location AndroidX Core dependency"
require_module_token "${LOCATION_BUILD}" 'implementation(libs.androidx.lifecycle.viewmodel)' "location ViewModel dependency"
require_module_token "${LOCATION_BUILD}" 'implementation(libs.kotlinx.coroutines.core)' "location Coroutines Core dependency"
require_module_token "${LOCATION_BUILD}" 'implementation(libs.amap.location)' "location AMap dependency"

PHOTO_UPLOAD_BUILD="${PROJECT_ROOT}/feature/photoupload/build.gradle.kts"
for token in \
  'implementation(libs.androidx.core.ktx)' \
  'implementation(libs.androidx.lifecycle.runtime.ktx)' \
  'implementation(libs.bundles.coil)' \
  'implementation(libs.crashreport)'; do
  forbid_module_token "${PHOTO_UPLOAD_BUILD}" "${token}" "photo-upload dependency"
done
require_module_token "${PHOTO_UPLOAD_BUILD}" 'implementation(libs.androidx.lifecycle.viewmodel)' "photo-upload ViewModel dependency"
require_module_token "${PHOTO_UPLOAD_BUILD}" 'implementation(libs.kotlinx.coroutines.core)' "photo-upload Coroutines Core dependency"

SERVICE_COUNTDOWN_BUILD="${PROJECT_ROOT}/feature/servicecountdown/build.gradle.kts"
forbid_module_token "${SERVICE_COUNTDOWN_BUILD}" 'implementation(libs.androidx.core.ktx)' "service-countdown Core KTX dependency"
forbid_module_token "${SERVICE_COUNTDOWN_BUILD}" 'implementation(libs.kotlinx.coroutines.android)' "service-countdown Coroutines Android dependency"
require_module_token "${SERVICE_COUNTDOWN_BUILD}" 'implementation(libs.androidx.lifecycle.viewmodel)' "service-countdown ViewModel dependency"
require_module_token "${SERVICE_COUNTDOWN_BUILD}" 'implementation(libs.kotlinx.coroutines.core)' "service-countdown Coroutines Core dependency"

IDENTIFICATION_BUILD="${PROJECT_ROOT}/feature/identification/build.gradle.kts"
forbid_module_token "${IDENTIFICATION_BUILD}" 'implementation(libs.crashreport)' "identification Bugly dependency"
require_module_token "${IDENTIFICATION_BUILD}" 'implementation(libs.kotlinx.coroutines.core)' "identification Coroutines Core dependency"

if [[ -d "${PROJECT_ROOT}/build-logic/convention/src" ]]; then
  while IFS= read -r source_file; do
    relative_path="${source_file#"${PROJECT_ROOT}/"}"
    if grep -En '(^|[^[:alnum:]_])(compileSdk|minSdk|targetSdk)[[:space:]]*=' "${source_file}" >/dev/null; then
      fail "convention-level SDK override found in ${relative_path}"
    fi
    if grep -En 'jvmToolchain\([[:space:]]*[0-9]+|JavaVersion\.toVersion\([[:space:]]*[0-9]+' "${source_file}" >/dev/null; then
      fail "numeric JDK override found in ${relative_path}; use appJdkVersion"
    fi
  done < <(find "${PROJECT_ROOT}/build-logic/convention/src" -type f -name '*.kt' -print)
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[android-build-baseline][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[android-build-baseline][PASS] Android build baseline is coherent."
echo "  - minSdk=${min_sdk}"
echo "  - targetSdk=${target_sdk}"
echo "  - compileSdk=${compile_sdk}"
echo "  - JDK=${jdk_version}"
echo "  - appVersion=${app_version_name} (${app_version_code})"
echo "  - AGP/settings-plugin=${agp_version}"
