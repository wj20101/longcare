#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
OWNERS_FILE=""
AGGREGATE_SCRIPT=""

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
    --aggregate-script)
      AGGREGATE_SCRIPT="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: verify_instrumentation_test_ownership.sh [--project-root <path>] [--owners-file <path>] [--aggregate-script <path>]"
      exit 0
      ;;
    *)
      echo "[instrumentation-test-ownership][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
OWNERS_FILE="${OWNERS_FILE:-${PROJECT_ROOT}/scripts/quality/instrumentation_test_modules.txt}"
AGGREGATE_SCRIPT="${AGGREGATE_SCRIPT:-${PROJECT_ROOT}/scripts/quality/run_connected_instrumentation_suite.sh}"
[[ "${OWNERS_FILE}" == /* ]] || OWNERS_FILE="${PROJECT_ROOT}/${OWNERS_FILE}"
[[ "${AGGREGATE_SCRIPT}" == /* ]] || AGGREGATE_SCRIPT="${PROJECT_ROOT}/${AGGREGATE_SCRIPT}"

ERRORS=()
REGISTERED_MODULES=()
ACTUAL_MODULES=()

fail() {
  ERRORS+=("$1")
}

contains_value() {
  local needle="$1"
  shift
  local value=""
  for value in "$@"; do
    [[ "${value}" == "${needle}" ]] && return 0
  done
  return 1
}

trim_line() {
  local value="$1"
  value="${value%%#*}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

module_directory() {
  local module="$1"
  local relative="${module#:}"
  relative="$(printf '%s' "${relative}" | tr ':' '/')"
  printf '%s/%s' "${PROJECT_ROOT}" "${relative}"
}

module_from_source() {
  local source_path="$1"
  local relative="${source_path#"${PROJECT_ROOT}/"}"
  local module_path="${relative%%/src/androidTest/*}"
  printf ':%s' "${module_path//\//:}"
}

first_test_source() {
  local module_dir="$1"
  local source_path=""
  while IFS= read -r source_path; do
    [[ -n "${source_path}" ]] || continue
    printf '%s' "${source_path}"
    return 0
  done < <(
    find "${module_dir}/src/androidTest" -type f \( -name '*.kt' -o -name '*.java' \) -print 2>/dev/null | sort
  )
  return 1
}

if [[ ! -f "${OWNERS_FILE}" ]]; then
  fail "ownership list is missing: ${OWNERS_FILE#"${PROJECT_ROOT}/"}; restore the reviewed module list"
else
  while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
    module="$(trim_line "${raw_line}")"
    [[ -n "${module}" ]] || continue
    if [[ ! "${module}" =~ ^:[a-z0-9][a-z0-9-]*(:[a-z0-9][a-z0-9-]*)*$ ]]; then
      fail "invalid Gradle module '${module}' in ${OWNERS_FILE#"${PROJECT_ROOT}/"}; use a module-qualified path such as :feature:login"
      continue
    fi
    if [[ ${#REGISTERED_MODULES[@]} -gt 0 ]] && contains_value "${module}" "${REGISTERED_MODULES[@]}"; then
      fail "duplicate module ${module} in ${OWNERS_FILE#"${PROJECT_ROOT}/"}; keep exactly one ownership entry"
      continue
    fi
    REGISTERED_MODULES+=("${module}")
  done < "${OWNERS_FILE}"
fi

if [[ ${#REGISTERED_MODULES[@]} -eq 0 ]]; then
  fail "ownership list ${OWNERS_FILE#"${PROJECT_ROOT}/"} contains no modules; register every non-empty src/androidTest owner"
else
  sorted_modules="$(printf '%s\n' "${REGISTERED_MODULES[@]}" | sort)"
  current_modules="$(printf '%s\n' "${REGISTERED_MODULES[@]}")"
  if [[ "${current_modules}" != "${sorted_modules}" ]]; then
    fail "ownership list ${OWNERS_FILE#"${PROJECT_ROOT}/"} is not in stable lexical order; sort entries without changing ownership"
  fi
fi

for source_group in app core feature; do
  [[ -d "${PROJECT_ROOT}/${source_group}" ]] || continue
  while IFS= read -r source_path; do
    [[ -n "${source_path}" ]] || continue
    module="$(module_from_source "${source_path}")"
    if [[ ${#ACTUAL_MODULES[@]} -eq 0 ]] || ! contains_value "${module}" "${ACTUAL_MODULES[@]}"; then
      ACTUAL_MODULES+=("${module}")
    fi
  done < <(
    find "${PROJECT_ROOT}/${source_group}" -type f \( -name '*.kt' -o -name '*.java' \) -path '*/src/androidTest/*' -print 2>/dev/null | sort
  )
done

for module in "${ACTUAL_MODULES[@]}"; do
  if [[ ${#REGISTERED_MODULES[@]} -eq 0 ]] || ! contains_value "${module}" "${REGISTERED_MODULES[@]}"; then
    module_dir="$(module_directory "${module}")"
    evidence="$(first_test_source "${module_dir}" || true)"
    fail "module ${module} has instrumentation source ${evidence#"${PROJECT_ROOT}/"} but is missing from ${OWNERS_FILE#"${PROJECT_ROOT}/"}; add the test APK owner"
  fi
done

if [[ ${#REGISTERED_MODULES[@]} -gt 0 ]]; then
  for module in "${REGISTERED_MODULES[@]}"; do
    module_dir="$(module_directory "${module}")"
    build_file="${module_dir}/build.gradle.kts"
    test_root="${module_dir}/src/androidTest"

    if [[ ! -d "${module_dir}" ]]; then
      fail "unknown module ${module}: directory ${module_dir#"${PROJECT_ROOT}/"} is missing; remove the stale entry or include the real module"
      continue
    fi
    if [[ ! -f "${PROJECT_ROOT}/settings.gradle.kts" ]] || ! grep -Fq "include(\"${module}\")" "${PROJECT_ROOT}/settings.gradle.kts"; then
      fail "unknown module ${module}: settings.gradle.kts does not include it; fix the ownership entry or Gradle settings"
    fi
    evidence="$(first_test_source "${module_dir}" || true)"
    if [[ -z "${evidence}" ]]; then
      fail "registered module ${module} has no Kotlin/Java source below ${test_root#"${PROJECT_ROOT}/"}; remove the stale empty owner instead of adding a fake test"
    fi
    if [[ ! -f "${build_file}" ]]; then
      fail "registered module ${module} is missing ${build_file#"${PROJECT_ROOT}/"}; restore its Android build contract"
      continue
    fi
    if ! grep -Eq 'testInstrumentationRunner[[:space:]]*=[[:space:]]*"androidx\.test\.runner\.AndroidJUnitRunner"' "${build_file}"; then
      fail "module ${module} test source ${evidence#"${PROJECT_ROOT}/"} lacks AndroidJUnitRunner in ${build_file#"${PROJECT_ROOT}/"}; declare testInstrumentationRunner"
    fi
    if ! grep -Eq 'androidTestImplementation[[:space:]]*\([[:space:]]*libs\.androidx\.test\.runner[[:space:]]*\)' "${build_file}"; then
      fail "module ${module} test source ${evidence#"${PROJECT_ROOT}/"} lacks the runner runtime in ${build_file#"${PROJECT_ROOT}/"}; add androidTestImplementation(libs.androidx.test.runner)"
    fi
  done
fi

if [[ ! -f "${AGGREGATE_SCRIPT}" ]]; then
  fail "supported aggregate script is missing: ${AGGREGATE_SCRIPT#"${PROJECT_ROOT}/"}; restore the module-scoped connected entry"
else
  if ! grep -Fq 'instrumentation_test_modules.txt' "${AGGREGATE_SCRIPT}"; then
    fail "aggregate script ${AGGREGATE_SCRIPT#"${PROJECT_ROOT}/"} does not consume instrumentation_test_modules.txt; use the reviewed ownership source"
  fi
  if ! grep -Fq ':connectedDebugAndroidTest' "${AGGREGATE_SCRIPT}"; then
    fail "aggregate script ${AGGREGATE_SCRIPT#"${PROJECT_ROOT}/"} does not generate module-qualified :connectedDebugAndroidTest tasks"
  fi
  if grep -Eq '[[:space:]]connectedDebugAndroidTest([[:space:]]|$)' "${AGGREGATE_SCRIPT}"; then
    fail "aggregate script ${AGGREGATE_SCRIPT#"${PROJECT_ROOT}/"} invokes root connectedDebugAndroidTest; generate only <module>:connectedDebugAndroidTest tasks"
  fi
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[instrumentation-test-ownership][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[instrumentation-test-ownership][PASS] ${#REGISTERED_MODULES[@]} connected test APK owner(s) match non-empty src/androidTest modules."
