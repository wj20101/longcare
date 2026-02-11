#!/usr/bin/env bash
set -euo pipefail

MANIFEST_PATH="${1:-app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml}"

if [[ ! -f "${MANIFEST_PATH}" ]]; then
  echo "Release merged manifest not found: ${MANIFEST_PATH}" >&2
  exit 1
fi

ALLOWED_EXPORTED_COMPONENTS=(
  "com.ytone.longcare.MainActivity"
)

TMP_EXPORTED="$(mktemp)"
trap 'rm -f "${TMP_EXPORTED}"' EXIT

awk '
function reset_component() {
  in_component = 0
  component_start_line = 0
  component_type = ""
  component_name = ""
  component_exported = ""
}
function emit_if_needed() {
  if (in_component && component_exported == "true" && component_name ~ /^com\.ytone\.longcare\./) {
    printf "%s %s\n", component_type, component_name
  }
  reset_component()
}
BEGIN {
  reset_component()
}
{
  if ($0 ~ /<(activity|service|receiver|provider)([[:space:]>]|$)/) {
    emit_if_needed()
    in_component = 1
    component_start_line = NR
    line = $0
    sub(/^[^<]*</, "", line)
    sub(/[[:space:]].*$/, "", line)
    sub(/>.*/, "", line)
    component_type = line
  }

  if (in_component && component_name == "" && $0 ~ /android:name="/) {
    line = $0
    sub(/.*android:name="/, "", line)
    sub(/".*$/, "", line)
    component_name = line
  }

  if (in_component && component_exported == "" && $0 ~ /android:exported="/) {
    line = $0
    sub(/.*android:exported="/, "", line)
    sub(/".*$/, "", line)
    component_exported = line
  }

  if (in_component && (($0 ~ /\/>/ && NR == component_start_line) || $0 ~ /<\/(activity|service|receiver|provider)>/)) {
    emit_if_needed()
  }
}
END {
  emit_if_needed()
}
' "${MANIFEST_PATH}" | sort -u > "${TMP_EXPORTED}"

if [[ ! -s "${TMP_EXPORTED}" ]]; then
  echo "No exported first-party components found in release manifest."
  exit 0
fi

VIOLATIONS=""
while IFS= read -r item; do
  [[ -z "${item}" ]] && continue
  component_name="${item#* }"

  is_allowed=false
  for allowed in "${ALLOWED_EXPORTED_COMPONENTS[@]}"; do
    if [[ "${component_name}" == "${allowed}" ]]; then
      is_allowed=true
      break
    fi
  done

  if [[ "${is_allowed}" == "false" ]]; then
    VIOLATIONS+="${item}"$'\n'
  fi
done < "${TMP_EXPORTED}"

if [[ -n "${VIOLATIONS}" ]]; then
  echo "Unexpected exported first-party components found in release manifest:" >&2
  printf '%s' "${VIOLATIONS}" | sed 's/^/  - /' >&2
  exit 1
fi

echo "Release exported component check passed."
echo "Allowed exported first-party components:"
printf '%s\n' "${ALLOWED_EXPORTED_COMPONENTS[@]}" | sed 's/^/  - /'
