#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
EXIT_CODE=0

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/verify_home_feature_boundary.sh [options]

Options:
  --project-root <path>  Project root path (default: .)
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "[home-boundary][FAIL] --project-root requires a path" >&2
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
      echo "[home-boundary][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${PROJECT_ROOT}" ]]; then
  echo "[home-boundary][FAIL] project root missing: ${PROJECT_ROOT}" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
APP_MAIN_ROOT="${PROJECT_ROOT}/app/src/main/kotlin"
FEATURE_MAIN_ROOT="${PROJECT_ROOT}/feature/home/src/main/kotlin"
ALLOWED_API="com.ytone.longcare.features.home.api.*, com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider"

relative_path() {
  printf '%s' "${1#${PROJECT_ROOT%/}/}"
}

report_violation() {
  local rule_id="$1"
  local file_path="$2"
  local fix="$3"
  echo "[home-boundary][FAIL] rule=${rule_id} file=$(relative_path "${file_path}")"
  echo "[home-boundary][ALLOW] api=${ALLOWED_API}"
  echo "[home-boundary][HINT] fix=${fix}"
  EXIT_CODE=1
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

print_matches() {
  local file_path="$1"
  local pattern="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -n -- "${pattern}" "${file_path}" || true
  else
    grep -En -- "${pattern}" "${file_path}" || true
  fi
}

scan_rule() {
  local rule_id="$1"
  local source_root="$2"
  local pattern="$3"
  local fix="$4"
  local file_path=""

  [[ -d "${source_root}" ]] || return 0
  while IFS= read -r file_path; do
    [[ -n "${file_path}" ]] || continue
    if matches "${file_path}" "${pattern}"; then
      report_violation "${rule_id}" "${file_path}" "${fix}"
      print_matches "${file_path}" "${pattern}"
    fi
  done < <(find "${source_root}" -type f -name '*.kt' -print | sort)
}

for required_root in "${APP_MAIN_ROOT}" "${FEATURE_MAIN_ROOT}"; do
  if [[ ! -d "${required_root}" ]]; then
    echo "[home-boundary][FAIL] required source root missing: $(relative_path "${required_root}")"
    EXIT_CODE=1
  fi
done
[[ "${EXIT_CODE}" -eq 0 ]] || exit "${EXIT_CODE}"

LEGACY_ROOTS=(
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/home/ui"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/maindashboard/api"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/maindashboard/ui"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/maindashboard/vm"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/nursing/api"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/nursing/ui"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/nursing/vm"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/profile/api"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/profile/ui"
  "${APP_MAIN_ROOT}/com/ytone/longcare/features/profile/vm"
)

for legacy_root in "${LEGACY_ROOTS[@]}"; do
  [[ -d "${legacy_root}" ]] || continue
  while IFS= read -r file_path; do
    [[ -n "${file_path}" ]] || continue
    report_violation \
      "legacy-app-home-implementation" \
      "${file_path}" \
      "move-the-implementation-to-feature-home-and-compose-it-through-HomeFeatureScreen"
  done < <(find "${legacy_root}" -type f -name '*.kt' -print | sort)
done

scan_rule \
  "app-imports-home-internal" \
  "${APP_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.features\.(home\.(ui|vm|nursing)|maindashboard|nursing\.|profile\.)' \
  "depend-only-on-the-reviewed-Home-public-API-or-the-login-reporting-contract"

scan_rule \
  "feature-imports-app-shell" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.(navigation|platform|presentation|integration)(\.|$)|^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.R(\.|$)' \
  "inject-an-action-renderer-or-value-and-use-feature-or-core-owned-resources"

scan_rule \
  "feature-imports-data-implementation" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.data\.|^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.common\.utils\.SystemConfigManager([[:space:]]|$)' \
  "depend-on-a-core-domain-contract-such-as-CompanyNameProvider"

scan_rule \
  "feature-uses-platform-type" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+android\.(app\.Activity|content\.(Context|Intent))([[:space:]]|$)' \
  "move-platform-launching-to-an-app-owned-action-adapter"

scan_rule \
  "feature-imports-vendor-sdk" \
  "${FEATURE_MAIN_ROOT}" \
  '^[[:space:]]*import[[:space:]]+com\.tencent\.|^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.common\.utils\.FaceVerificationManager([[:space:]]|$)' \
  "keep-vendor-types-behind-existing-domain-or-app-platform-adapters"

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[home-boundary] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[home-boundary] verification passed."
