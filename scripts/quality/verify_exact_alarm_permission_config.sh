#!/usr/bin/env bash
set -euo pipefail

MANIFEST_PATH="${1:-app/src/main/AndroidManifest.xml}"

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "Manifest not found: ${MANIFEST_PATH}" >&2
  exit 1
fi

TMP_MATCHES="$(mktemp)"
trap 'rm -f "${TMP_MATCHES}"' EXIT

awk '
BEGIN {
  in_perm = 0
  block = ""
}
{
  if ($0 ~ /<uses-permission[[:space:]]/) {
    in_perm = 1
    block = $0 "\n"
    if ($0 ~ /\/>/) {
      in_perm = 0
      if (block ~ /android\.permission\.SCHEDULE_EXACT_ALARM/) {
        printf "%s", block
      }
      block = ""
    }
    next
  }

  if (in_perm) {
    block = block $0 "\n"
    if ($0 ~ /\/>/) {
      in_perm = 0
      if (block ~ /android\.permission\.SCHEDULE_EXACT_ALARM/) {
        printf "%s", block
      }
      block = ""
    }
  }
}
' "${MANIFEST_PATH}" > "${TMP_MATCHES}"

if [[ ! -s "${TMP_MATCHES}" ]]; then
  echo "Missing required permission declaration: android.permission.SCHEDULE_EXACT_ALARM" >&2
  exit 1
fi

if grep -q "maxSdkVersion" "${TMP_MATCHES}"; then
  echo "Invalid exact alarm config: SCHEDULE_EXACT_ALARM must not define maxSdkVersion." >&2
  echo "Detected block:" >&2
  sed 's/^/  /' "${TMP_MATCHES}" >&2
  exit 1
fi

echo "Exact alarm permission config check passed."
