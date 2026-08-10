#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
ALLOWLIST_FILE="${2:-${PROJECT_ROOT}/scripts/quality/module_dependency_allowlist.txt}"

if [[ ! -f "${ALLOWLIST_FILE}" ]]; then
  echo "[module-deps][FAIL] allowlist file not found: ${ALLOWLIST_FILE}" >&2
  exit 1
fi

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

module_from_build_file() {
  local build_file="$1"
  local relative_path="${build_file#${PROJECT_ROOT}/}"
  local module_path="${relative_path%/build.gradle.kts}"
  printf ':%s' "${module_path//\//:}"
}

NORMALIZED_ALLOWLIST="$(mktemp)"
SEEN_MODULES_FILE="$(mktemp)"
trap 'rm -f "${NORMALIZED_ALLOWLIST}" "${SEEN_MODULES_FILE}"' EXIT

while IFS='=' read -r raw_source raw_targets; do
  source_module="$(trim "${raw_source:-}")"
  [[ -z "${source_module}" ]] && continue
  [[ "${source_module}" == \#* ]] && continue
  targets="$(trim "${raw_targets:-}")"
  printf '%s=%s\n' "${source_module}" "${targets}" >> "${NORMALIZED_ALLOWLIST}"
done < "${ALLOWLIST_FILE}"

if [[ ! -s "${NORMALIZED_ALLOWLIST}" ]]; then
  echo "[module-deps][FAIL] allowlist is empty: ${ALLOWLIST_FILE}" >&2
  exit 1
fi

BUILD_FILES="$({
  find "${PROJECT_ROOT}/app" -maxdepth 2 -type f -name 'build.gradle.kts' 2>/dev/null
  find "${PROJECT_ROOT}/baselineprofile" -maxdepth 2 -type f -name 'build.gradle.kts' 2>/dev/null
  find "${PROJECT_ROOT}/core" -maxdepth 2 -type f -name 'build.gradle.kts' 2>/dev/null
  find "${PROJECT_ROOT}/feature" -maxdepth 2 -type f -name 'build.gradle.kts' 2>/dev/null
} | sort)"

if [[ -z "${BUILD_FILES}" ]]; then
  echo "[module-deps][FAIL] no module build files found under ${PROJECT_ROOT}" >&2
  exit 1
fi

EXIT_CODE=0

while IFS= read -r build_file; do
  [[ -z "${build_file}" ]] && continue

  source_module="$(module_from_build_file "${build_file}")"
  printf '%s\n' "${source_module}" >> "${SEEN_MODULES_FILE}"

  allowlist_entry="$(awk -F'=' -v module="${source_module}" '$1 == module {print $0}' "${NORMALIZED_ALLOWLIST}" | head -n 1)"
  if [[ -z "${allowlist_entry}" ]]; then
    echo "[module-deps][FAIL] missing allowlist entry for ${source_module}" >&2
    EXIT_CODE=1
    continue
  fi

  allowed_targets="${allowlist_entry#*=}"
  allowed_targets=" $(trim "${allowed_targets}") "

  dependency_files=("${build_file}")
  module_dependency_script="$(dirname "${build_file}")/dependencies.gradle.kts"
  if [[ -f "${module_dependency_script}" ]]; then
    dependency_files+=("${module_dependency_script}")
  fi

  checked_count=0
  while IFS= read -r target_module; do
    [[ -z "${target_module}" ]] && continue
    checked_count=$((checked_count + 1))

    if [[ "${allowed_targets}" != *" ${target_module} "* ]]; then
      echo "[module-deps][FAIL] ${source_module} -> ${target_module} is not allowlisted" >&2
      EXIT_CODE=1
    fi
  done <<EOF_TARGETS
$(
    {
      grep -h -oE '(project|projectDependency)\("[:][^"]+"\)' "${dependency_files[@]}" || true
    } |
      sed -E 's#(project|projectDependency)\("(:[^"]+)"\)#\2#' |
      sort -u
)
EOF_TARGETS

  echo "[module-deps] ${source_module}: ${checked_count} project deps checked"
done <<EOF_BUILD_FILES
${BUILD_FILES}
EOF_BUILD_FILES

while IFS='=' read -r allowlisted_module _; do
  [[ -z "${allowlisted_module}" ]] && continue
  if ! grep -Fxq "${allowlisted_module}" "${SEEN_MODULES_FILE}"; then
    echo "[module-deps][FAIL] allowlist contains unknown module: ${allowlisted_module}" >&2
    EXIT_CODE=1
  fi
done < "${NORMALIZED_ALLOWLIST}"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[module-deps] dependency whitelist verification failed." >&2
  exit "${EXIT_CODE}"
fi

echo "[module-deps] dependency whitelist verification passed."
