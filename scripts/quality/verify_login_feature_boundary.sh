#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
EXIT_CODE=0

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/verify_login_feature_boundary.sh [options]

Options:
  --project-root <path>  Project root path (default: .)
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "[login-boundary][FAIL] --project-root requires a path" >&2
        exit 1
      fi
      PROJECT_ROOT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[login-boundary][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${PROJECT_ROOT}" ]]; then
  echo "[login-boundary][FAIL] project root missing: ${PROJECT_ROOT}" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
APP_MAIN_ROOT="${PROJECT_ROOT}/app/src/main/kotlin"
LEGACY_UI_ROOT="${APP_MAIN_ROOT}/com/ytone/longcare/features/login/ui"
LEGACY_SHEET="${APP_MAIN_ROOT}/com/ytone/longcare/presentation/validation/LoginValidationEntrySheet.kt"
FEATURE_MAIN_ROOT="${PROJECT_ROOT}/feature/login/src/main/kotlin"

relative_path() {
  printf '%s' "${1#${PROJECT_ROOT%/}/}"
}

report_violation() {
  local rule_id="$1"
  local file_path="$2"
  local fix="$3"
  echo "[login-boundary][FAIL] rule=${rule_id} file=$(relative_path "${file_path}")"
  echo "[login-boundary][HINT] fix=${fix}"
  EXIT_CODE=1
}

print_matches() {
  local file_path="$1"
  local pattern="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -n -- "${pattern}" "${file_path}" || true
  else
    grep -En -- "${pattern}" "${file_path}" || true
  fi
}

matches() {
  local file_path="$1"
  local pattern="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -q -- "${pattern}" "${file_path}"
  else
    grep -Eq -- "${pattern}" "${file_path}"
  fi
}

scan_import_rule() {
  local rule_id="$1"
  local source_root="$2"
  local pattern="$3"
  local fix="$4"
  local file_path

  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    if matches "${file_path}" "${pattern}"; then
      report_violation "${rule_id}" "${file_path}" "${fix}"
      print_matches "${file_path}" "${pattern}"
    fi
  done < <(find "${source_root}" -type f -name '*.kt' -print | sort)
}

for required_root in "${APP_MAIN_ROOT}" "${FEATURE_MAIN_ROOT}"; do
  if [[ ! -d "${required_root}" ]]; then
    echo "[login-boundary][FAIL] required source root missing: $(relative_path "${required_root}")"
    EXIT_CODE=1
  fi
done

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  exit "${EXIT_CODE}"
fi

if [[ -d "${LEGACY_UI_ROOT}" ]]; then
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    report_violation \
      "legacy-app-login-ui" \
      "${file_path}" \
      "move-the-login-screen-to-feature-login-and-use-LoginFeatureScreen"
  done < <(find "${LEGACY_UI_ROOT}" -type f -name '*.kt' -print | sort)
fi

if [[ -f "${LEGACY_SHEET}" ]]; then
  report_violation \
    "legacy-app-login-validation-sheet" \
    "${LEGACY_SHEET}" \
    "keep-the-validation-sheet-in-feature-login-and-inject-app-owned-actions"
fi

scan_import_rule \
  "feature-imports-app-shell" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.(navigation|platform|presentation)(\.|$)|^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.R(\.|$)' \
  "inject-a-public-login-action-or-use-feature-local-resources"

scan_import_rule \
  "feature-starts-platform-component" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+android\.(app\.Activity|content\.Intent)(\.|$)' \
  "move-Activity-and-Intent-launching-to-the-app-owned-login-action-adapter"

scan_import_rule \
  "app-imports-login-internal" \
  "${APP_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.features\.login\.(ui|vm)(\.|$)' \
  "depend-only-on-com.ytone.longcare.feature.login.api"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[login-boundary] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[login-boundary] verification passed."
