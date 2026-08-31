#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
SOURCES=()
OWNED_FIELD_SOURCES=()
OWNED_FIELD_KEYS=()
OWNED_FIELD_ROOTS=()

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
    --owned-field)
      if [[ $# -lt 4 ]]; then
        echo "[instrumentation-smoke-classes][FAIL] --owned-field requires <source> <field> <androidTest-root>" >&2
        exit 1
      fi
      OWNED_FIELD_SOURCES+=("$2")
      OWNED_FIELD_KEYS+=("$3")
      OWNED_FIELD_ROOTS+=("$4")
      shift 4
      ;;
    -h|--help)
      echo "Usage: verify_instrumentation_smoke_classes.sh [--project-root <path>] [--source <path>]... [--owned-field <source> <field> <androidTest-root>]..."
      exit 0
      ;;
    *)
      echo "[instrumentation-smoke-classes][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
if [[ ${#SOURCES[@]} -eq 0 && ${#OWNED_FIELD_SOURCES[@]} -eq 0 ]]; then
  SOURCES=(
    "scripts/quality/run_target_sdk_local_smoke.sh"
    "scripts/quality/affected-modules.sh"
  )
  MATRIX_SOURCE="scripts/quality/target_platform_test_matrix.properties"
  OWNED_FIELD_SOURCES=(
    "${MATRIX_SOURCE}"
    "${MATRIX_SOURCE}"
    "${MATRIX_SOURCE}"
    "${MATRIX_SOURCE}"
    "${MATRIX_SOURCE}"
    "${MATRIX_SOURCE}"
  )
  OWNED_FIELD_KEYS=(
    "current_target_smoke_classes"
    "candidate_smoke_classes"
    "current_target_home_feature_smoke_classes"
    "candidate_target_home_feature_smoke_classes"
    "current_target_login_feature_smoke_classes"
    "candidate_target_login_feature_smoke_classes"
  )
  OWNED_FIELD_ROOTS=(
    "app/src/androidTest"
    "app/src/androidTest"
    "feature/home/src/androidTest"
    "feature/home/src/androidTest"
    "feature/login/src/androidTest"
    "feature/login/src/androidTest"
  )
fi

ERRORS=()
CHECKED=0

resolve_path() {
  local path_value="$1"
  if [[ "${path_value}" == /* ]]; then
    printf '%s' "${path_value}"
  else
    printf '%s/%s' "${PROJECT_ROOT}" "${path_value}"
  fi
}

validate_class_in_roots() {
  local fqcn="$1"
  local source_label="$2"
  shift 2
  local relative_class_path="${fqcn//.//}.kt"
  local class_name="${fqcn##*.}"
  local candidate_root=""
  local java_path=""
  local kotlin_path=""
  local matches=()

  CHECKED=$((CHECKED + 1))
  for candidate_root in "$@"; do
    java_path="${candidate_root}/java/${relative_class_path}"
    kotlin_path="${candidate_root}/kotlin/${relative_class_path}"
    [[ -f "${java_path}" ]] && matches+=("${java_path}")
    [[ -f "${kotlin_path}" ]] && matches+=("${kotlin_path}")
  done

  if [[ ${#matches[@]} -eq 0 ]]; then
    ERRORS+=("missing instrumentation class ${fqcn} referenced by ${source_label}; expected owner root(s): $*")
    return 0
  fi
  if [[ ${#matches[@]} -gt 1 ]]; then
    joined_matches="$(printf '%s,' "${matches[@]}")"
    ERRORS+=("ambiguous instrumentation class ${fqcn} referenced by ${source_label}; declarations: ${joined_matches%,}; keep one test APK owner")
    return 0
  fi

  resolved_path="${matches[0]}"
  if ! grep -Eq "(^|[[:space:]])class[[:space:]]+${class_name}([[:space:](:<{]|$)" "${resolved_path}"; then
    ERRORS+=("instrumentation source ${resolved_path#"${PROJECT_ROOT}/"} does not declare ${fqcn}; referenced by ${source_label}")
  fi
}

if [[ ${#SOURCES[@]} -gt 0 ]]; then
  for source in "${SOURCES[@]}"; do
    source="$(resolve_path "${source}")"
    source_label="${source#"${PROJECT_ROOT}/"}"
    if [[ ! -f "${source}" ]]; then
      ERRORS+=("source file is missing: ${source_label}")
      continue
    fi

    candidate_roots=()
    for source_group in app core feature; do
      [[ -d "${PROJECT_ROOT}/${source_group}" ]] || continue
      while IFS= read -r android_test_root; do
        [[ -n "${android_test_root}" ]] && candidate_roots+=("${android_test_root}")
      done < <(find "${PROJECT_ROOT}/${source_group}" -type d -path '*/src/androidTest' -print 2>/dev/null | sort)
    done

    while IFS= read -r fqcn; do
      [[ -n "${fqcn}" ]] || continue
      if [[ ${#candidate_roots[@]} -eq 0 ]]; then
        CHECKED=$((CHECKED + 1))
        ERRORS+=("no instrumentation source roots exist for ${fqcn} referenced by ${source_label}")
      else
        validate_class_in_roots "${fqcn}" "${source_label}" "${candidate_roots[@]}"
      fi
    done < <(grep -Eo 'com\.ytone\.longcare(\.[A-Za-z_][A-Za-z0-9_]*)+' "${source}" | sort -u)
  done
fi

if [[ ${#OWNED_FIELD_SOURCES[@]} -gt 0 ]]; then
  for ((index = 0; index < ${#OWNED_FIELD_SOURCES[@]}; index++)); do
    source="$(resolve_path "${OWNED_FIELD_SOURCES[index]}")"
    field="${OWNED_FIELD_KEYS[index]}"
    owner_root="$(resolve_path "${OWNED_FIELD_ROOTS[index]}")"
    source_label="${source#"${PROJECT_ROOT}/"}:${field}"

    if [[ ! -f "${source}" ]]; then
      ERRORS+=("source file is missing: ${source#"${PROJECT_ROOT}/"}")
      continue
    fi
    field_count="$(grep -Ec "^[[:space:]]*${field}[[:space:]]*=" "${source}" || true)"
    if [[ "${field_count}" -ne 1 ]]; then
      ERRORS+=("owned selector field ${source_label} must appear exactly once (found ${field_count})")
      continue
    fi
    if [[ ! -d "${owner_root}" ]]; then
      ERRORS+=("owned selector field ${source_label} points to missing test APK root ${owner_root#"${PROJECT_ROOT}/"}")
      continue
    fi

    class_list="$(awk -F= -v key="${field}" '
      $1 ~ "^[[:space:]]*" key "[[:space:]]*$" {
        value = substr($0, index($0, "=") + 1)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        print value
      }
    ' "${source}")"
    if [[ -z "${class_list}" ]]; then
      ERRORS+=("owned selector field ${source_label} must not be empty")
      continue
    fi

    IFS=',' read -r -a owned_classes <<< "${class_list}"
    for fqcn in "${owned_classes[@]}"; do
      fqcn="${fqcn#"${fqcn%%[![:space:]]*}"}"
      fqcn="${fqcn%"${fqcn##*[![:space:]]}"}"
      [[ -n "${fqcn}" ]] || continue
      validate_class_in_roots "${fqcn}" "${source_label}" "${owner_root}"
    done
  done
fi

if [[ "${CHECKED}" -eq 0 ]]; then
  ERRORS+=("no fully qualified instrumentation classes were found in configured sources")
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[instrumentation-smoke-classes][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[instrumentation-smoke-classes][PASS] ${CHECKED} referenced class declaration(s) resolved in their configured test APK roots."
