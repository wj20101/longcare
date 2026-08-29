#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
DOC_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --doc)
      DOC_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: verify_tech_stack_baseline.sh [--project-root <path>] [--doc <path>]"
      exit 0
      ;;
    *)
      echo "[tech-stack-baseline][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
SETTINGS_FILE="${PROJECT_ROOT}/settings.gradle.kts"
CONSTANTS_FILE="${PROJECT_ROOT}/constants.gradle.kts"
CATALOG_FILE="${PROJECT_ROOT}/gradle/libs.versions.toml"
WRAPPER_FILE="${PROJECT_ROOT}/gradle/wrapper/gradle-wrapper.properties"
if [[ -z "${DOC_FILE}" ]]; then
  DOC_FILE="${PROJECT_ROOT}/docs/architecture/tech-stack.md"
elif [[ "${DOC_FILE}" != /* ]]; then
  DOC_FILE="${PROJECT_ROOT}/${DOC_FILE}"
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/quality/android_build_values.sh
source "${SCRIPT_DIR}/android_build_values.sh"
# shellcheck source=scripts/quality/gradle_constants.sh
source "${SCRIPT_DIR}/gradle_constants.sh"

ERRORS=()
fail() { ERRORS+=("$1"); }

for required in "${SETTINGS_FILE}" "${CONSTANTS_FILE}" "${CATALOG_FILE}" "${WRAPPER_FILE}" "${DOC_FILE}"; do
  [[ -f "${required}" ]] || fail "required source is missing: ${required#"${PROJECT_ROOT}/"}"
done

require_doc_value() {
  local label="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    fail "cannot resolve managed value for ${label}"
  elif ! grep -Fq "${value}" "${DOC_FILE}"; then
    fail "documentation drift for ${label}: expected '${value}' in ${DOC_FILE#"${PROJECT_ROOT}/"}"
  fi
}

if [[ -f "${DOC_FILE}" ]]; then
  compile_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "compileSdk")"
  target_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "targetSdk")"
  min_sdk="$(read_android_settings_release "${SETTINGS_FILE}" "minSdk")"
  jdk="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appJdkVersion")"
  version_code="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appVersionCode")"
  version_name="$(read_gradle_extra_value "${CONSTANTS_FILE}" "appVersionName")"
  wrapper="$(sed -nE 's#^distributionUrl=.*gradle-([0-9.]+)-[^/]+\.zip#\1#p' "${WRAPPER_FILE}" | head -n1)"

  require_doc_value "application version" "| 版本 | \`${version_name} (${version_code})\` |"
  require_doc_value "compileSdk" "| \`compileSdk\` | ${compile_sdk} | \`settings.gradle.kts\` |"
  require_doc_value "targetSdk" "| \`targetSdk\` | ${target_sdk} | \`settings.gradle.kts\` |"
  require_doc_value "minSdk" "| \`minSdk\` | ${min_sdk} | \`settings.gradle.kts\` |"
  require_doc_value "JDK" "| JDK / JVM toolchain | ${jdk} |"
  require_doc_value "Gradle Wrapper" "| Gradle Wrapper | ${wrapper} |"

  agp="$(read_toml_version "${CATALOG_FILE}" agp)"
  kotlin="$(read_toml_version "${CATALOG_FILE}" kotlin)"
  ksp="$(read_toml_version "${CATALOG_FILE}" ksp)"
  compose_bom="$(read_toml_version "${CATALOG_FILE}" composeBom)"
  navigation="$(read_toml_version "${CATALOG_FILE}" androidxNavigation)"
  camera="$(read_toml_version "${CATALOG_FILE}" androidxCamera)"
  coil="$(read_toml_version "${CATALOG_FILE}" coil)"
  datetime="$(read_toml_version "${CATALOG_FILE}" kotlinxDatetime)"
  baseline_profile="$(read_toml_version "${CATALOG_FILE}" androidxBaselineProfile)"
  benchmark="$(read_toml_version "${CATALOG_FILE}" androidxBenchmark)"

  require_doc_value "Android Gradle Plugin" "| Android Gradle Plugin | ${agp} |"
  require_doc_value "Kotlin" "| Kotlin | ${kotlin} |"
  require_doc_value "KSP" "| KSP | ${ksp} |"
  require_doc_value "Jetpack Compose BOM" "| UI | Jetpack Compose BOM | ${compose_bom} |"
  require_doc_value "Navigation Compose" "| Navigation | Navigation Compose | ${navigation} |"
  require_doc_value "CameraX" "| Camera | CameraX | ${camera} |"
  require_doc_value "Coil" "| Images | Coil | ${coil} |"
  require_doc_value "kotlinx-datetime" "| Date/time | kotlinx-datetime | ${datetime} |"
  require_doc_value "Baseline Profile / Benchmark" "| Performance | Baseline Profile / Macrobenchmark | ${baseline_profile} / ${benchmark} |"
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[tech-stack-baseline][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[tech-stack-baseline][PASS] documented build and dependency facts match their sources."
