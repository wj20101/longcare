#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

SOURCE_DIRS=(
  "${ROOT_DIR}/app/src/main/kotlin"
  "${ROOT_DIR}/core"
  "${ROOT_DIR}/feature"
)

TMP_RESULTS="$(mktemp)"
trap 'rm -f "${TMP_RESULTS}"' EXIT

scan_forbidden_pattern() {
  local pattern="$1"
  local allow_pattern="$2"
  local check_name="$3"
  local suggestion="$4"
  local matches=""

  if command -v rg >/dev/null 2>&1; then
    matches="$(rg -n --glob '*.kt' "${pattern}" "${SOURCE_DIRS[@]}" 2>/dev/null || true)"
  else
    matches="$(grep -REn --include='*.kt' "${pattern}" "${SOURCE_DIRS[@]}" 2>/dev/null || true)"
  fi

  if [[ -n "${allow_pattern}" && -n "${matches}" ]]; then
    matches="$(printf '%s\n' "${matches}" | grep -Ev "${allow_pattern}" || true)"
  fi

  if [[ -n "${matches}" ]]; then
    {
      echo "[FAIL] ${check_name}"
      echo "Use: ${suggestion}"
      printf '%s\n' "${matches}"
      echo ""
    } >> "${TMP_RESULTS}"
  else
    echo "[PASS] ${check_name}"
  fi
}

scan_forbidden_pattern \
  '\bPendingIntent\.(getActivity|getBroadcast|getService)\(' \
  '' \
  'Disallow direct PendingIntent factory usage in app code' \
  'androidx.core.app.PendingIntentCompat.getActivity/getBroadcast/getService'

scan_forbidden_pattern \
  '\bNotificationChannel[[:space:]]*\(' \
  '' \
  'Disallow direct NotificationChannel constructor usage in app code' \
  'androidx.core.app.NotificationChannelCompat.Builder + NotificationManagerCompat'

scan_forbidden_pattern \
  '\bregisterReceiver[[:space:]]*\(' \
  'ContextCompat\.registerReceiver[[:space:]]*\(' \
  'Disallow direct registerReceiver usage in app code' \
  'androidx.core.content.ContextCompat.registerReceiver'

scan_forbidden_pattern \
  '\bstartForeground[[:space:]]*\(' \
  'ServiceCompat\.startForeground[[:space:]]*\(' \
  'Disallow direct startForeground usage in app code' \
  'androidx.core.app.ServiceCompat.startForeground'

if [[ -s "${TMP_RESULTS}" ]]; then
  echo "Jetpack compat API guard failed:"
  cat "${TMP_RESULTS}"
  exit 1
fi

echo "Jetpack compat API guard passed."
