#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
SOURCES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --source)
      SOURCES+=("${2:-}")
      shift 2
      ;;
    -h|--help)
      echo "Usage: verify_instrumentation_smoke_classes.sh [--project-root <path>] [--source <path>]..."
      exit 0
      ;;
    *)
      echo "[instrumentation-smoke-classes][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
if [[ ${#SOURCES[@]} -eq 0 ]]; then
  SOURCES=(
    "scripts/quality/run_target_sdk_local_smoke.sh"
    "scripts/quality/affected-modules.sh"
    "scripts/quality/target_platform_test_matrix.properties"
  )
fi

ERRORS=()
CHECKED=0

for source in "${SOURCES[@]}"; do
  if [[ "${source}" != /* ]]; then
    source="${PROJECT_ROOT}/${source}"
  fi
  source_label="${source#"${PROJECT_ROOT}/"}"
  if [[ ! -f "${source}" ]]; then
    ERRORS+=("source file is missing: ${source_label}")
    continue
  fi

  while IFS= read -r fqcn; do
    [[ -n "${fqcn}" ]] || continue
    CHECKED=$((CHECKED + 1))
    relative_class_path="${fqcn//.//}.kt"
    java_path="${PROJECT_ROOT}/app/src/androidTest/java/${relative_class_path}"
    kotlin_path="${PROJECT_ROOT}/app/src/androidTest/kotlin/${relative_class_path}"
    class_name="${fqcn##*.}"
    resolved_path=""
    [[ -f "${java_path}" ]] && resolved_path="${java_path}"
    [[ -f "${kotlin_path}" ]] && resolved_path="${kotlin_path}"

    if [[ -z "${resolved_path}" ]]; then
      ERRORS+=("missing instrumentation class ${fqcn} referenced by ${source_label}")
    elif ! grep -Eq "(^|[[:space:]])class[[:space:]]+${class_name}([[:space:](:<{]|$)" "${resolved_path}"; then
      ERRORS+=("instrumentation source ${resolved_path#"${PROJECT_ROOT}/"} does not declare ${fqcn}; referenced by ${source_label}")
    fi
  done < <(grep -Eo 'com\.ytone\.longcare(\.[A-Za-z_][A-Za-z0-9_]*)+' "${source}" | sort -u)
done

if [[ ${CHECKED} -eq 0 ]]; then
  ERRORS+=("no fully qualified instrumentation classes were found in configured sources")
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[instrumentation-smoke-classes][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[instrumentation-smoke-classes][PASS] ${CHECKED} referenced class declaration(s) resolved."
