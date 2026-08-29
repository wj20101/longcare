#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
EXIT_CODE=0

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/verify_user_storage_boundaries.sh [options]

Options:
  --project-root <path>  Project root path (default: .)
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "[user-storage-boundaries][FAIL] --project-root requires a path" >&2
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
      echo "[user-storage-boundaries][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${PROJECT_ROOT}" ]]; then
  echo "[user-storage-boundaries][FAIL] project root missing: ${PROJECT_ROOT}" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
SOURCE_ROOTS=(
  "${PROJECT_ROOT}/app/src/main/kotlin"
  "${PROJECT_ROOT}/core"
  "${PROJECT_ROOT}/feature"
)
BUSINESS_PREFERENCE_ROOTS=(
  "${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/features"
  "${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/data"
  "${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/db"
  "${PROJECT_ROOT}/core/data/src/main/kotlin"
  "${PROJECT_ROOT}/feature"
)

relative_path() {
  printf '%s' "${1#${PROJECT_ROOT%/}/}"
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

collect_kotlin_files() {
  local root
  for root in "$@"; do
    if [[ -d "${root}" ]]; then
      find "${root}" -type f -path '*/src/main/*' -name '*.kt' -print
    fi
  done | sort -u
}

is_allowed_path() {
  local candidate="$1"
  shift
  local allowed
  for allowed in "$@"; do
    if [[ "${candidate}" == "${allowed}" ]]; then
      return 0
    fi
  done
  return 1
}

report_violation() {
  local rule_id="$1"
  local file_path="$2"
  echo "[user-storage-boundaries][FAIL] rule=${rule_id} file=$(relative_path "${file_path}")"
  EXIT_CODE=1
}

scan_outside_allowlist() {
  local rule_id="$1"
  local pattern="$2"
  shift 2
  local allowed_paths=("$@")
  local file_path
  local relative
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    if matches "${file_path}" "${pattern}"; then
      relative="$(relative_path "${file_path}")"
      if [[ "${#allowed_paths[@]}" -eq 0 ]] || ! is_allowed_path "${relative}" "${allowed_paths[@]}"; then
        report_violation "${rule_id}" "${file_path}"
      fi
    fi
  done < <(collect_kotlin_files "${SOURCE_ROOTS[@]}")
}

scan_business_preferences() {
  local file_path
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    if matches "${file_path}" '(getSharedPreferences[[:space:]]*\(|(^|[^[:alnum:]_])SharedPreferences([^[:alnum:]_]|$))'; then
      report_violation "unscoped-user-business-shared-preferences" "${file_path}"
    fi
  done < <(collect_kotlin_files "${BUSINESS_PREFERENCE_ROOTS[@]}")
}

scan_order_background_identity() {
  local file_path
  local primitive_pattern='(PendingIntent(Compat)?[[:space:]]*\.[[:space:]]*get(Activity|Broadcast|Service|ForegroundService)|enqueueUniqueWork[[:space:]]*\(|OneTimeWorkRequestBuilder|NotificationManager(Compat)?[[:space:]]*\.|notificationManager[^[:space:]]*[[:space:]]*\.[[:space:]]*(notify|cancel)[[:space:]]*\()'
  local scope_evidence='(UserTaskIdentity|TaskCodec|taskCodec|taskIdentity|namespaceId|sessionEpoch|dataUri|requestCode)'
  while IFS= read -r file_path; do
    [[ -z "${file_path}" ]] && continue
    if matches "${file_path}" '(^|[^[:alnum:]_])orderId([^[:alnum:]_]|$)' && matches "${file_path}" "${primitive_pattern}"; then
      if ! matches "${file_path}" "${scope_evidence}"; then
        report_violation "order-id-only-background-identity" "${file_path}"
      fi
    fi
  done < <(collect_kotlin_files "${SOURCE_ROOTS[@]}")
}

echo "[user-storage-boundaries] checking project: ${PROJECT_ROOT}"

scan_outside_allowlist \
  "room-creation-outside-user-factory" \
  'Room[[:space:]]*\.[[:space:]]*(databaseBuilder|inMemoryDatabaseBuilder)[[:space:]]*\(' \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDatabaseFactory.kt"

scan_outside_allowlist \
  "datastore-creation-outside-registry" \
  '(PreferenceDataStoreFactory|DataStoreFactory)[[:space:]]*\.[[:space:]]*create[[:space:]]*(\(|\{)|preferencesDataStore[[:space:]]*\(' \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDataStoreRegistry.kt" \
  "core/data/src/main/kotlin/com/ytone/longcare/di/ProcessSessionDataStoreModule.kt"

scan_outside_allowlist \
  "direct-business-dao-access" \
  '(:[[:space:]]*[A-Za-z0-9_.]*Dao([^[:alnum:]_]|$)|^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.data\.database\.dao\.)' \
  "core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt"

scan_outside_allowlist \
  "global-database-handle-outside-user-access" \
  '^[[:space:]]*import[[:space:]]+com\.ytone\.longcare\.data\.database\.LongCareDatabase' \
  "core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDatabaseFactory.kt" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDatabaseAccess.kt" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserStorageRegistry.kt" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/repository/OrderRoomSyncDelegate.kt"

scan_outside_allowlist \
  "bare-user-id-filename" \
  '("user_[[:space:]]*\$|"user_"[[:space:]]*(\+|\.plus)|format[[:space:]]*\([[:space:]]*"user_%)'

scan_business_preferences
scan_order_background_identity

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[user-storage-boundaries] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[user-storage-boundaries] verification passed."
