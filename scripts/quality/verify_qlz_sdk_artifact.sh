#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APPROVED_AAR_NAME="qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar"
APPROVED_AAR_SHA256="572294bb71c513a685aa4a62aea6a2589a2da4979c80cf4b658a32d09467c688"
AAR_PATH="${ROOT_DIR}/app/libs/${APPROVED_AAR_NAME}"
LOCAL_LIBS_DIR="${ROOT_DIR}/app/libs"
ARCHIVE_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aar-path)
      [[ $# -ge 2 ]] || { echo "[qlz-artifact][FAIL] missing value for $1" >&2; exit 2; }
      AAR_PATH="$2"
      shift 2
      ;;
    --local-libs-dir)
      [[ $# -ge 2 ]] || { echo "[qlz-artifact][FAIL] missing value for $1" >&2; exit 2; }
      LOCAL_LIBS_DIR="$2"
      shift 2
      ;;
    --archive-path)
      [[ $# -ge 2 ]] || { echo "[qlz-artifact][FAIL] missing value for $1" >&2; exit 2; }
      ARCHIVE_PATH="$2"
      shift 2
      ;;
    *)
      echo "[qlz-artifact][FAIL] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

sha256_file() {
  local file_path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file_path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file_path}" | awk '{print $1}'
  else
    echo "[qlz-artifact][FAIL] shasum or sha256sum is required" >&2
    exit 2
  fi
}

canonical_existing_path() {
  local file_path="$1"
  local directory
  directory="$(cd "$(dirname "${file_path}")" && pwd -P)"
  printf '%s/%s' "${directory}" "$(basename "${file_path}")"
}

if [[ ! -d "${LOCAL_LIBS_DIR}" ]]; then
  echo "[qlz-artifact][FAIL] local libs directory is missing" >&2
  exit 1
fi

if [[ "$(basename "${AAR_PATH}")" != "${APPROVED_AAR_NAME}" ]]; then
  echo "[qlz-artifact][FAIL] QLZ AAR filename is not the approved vendor filename" >&2
  exit 1
fi

if [[ ! -f "${AAR_PATH}" ]]; then
  echo "[qlz-artifact][FAIL] approved QLZ AAR is missing" >&2
  exit 1
fi

local_qlz_count="$(find "${LOCAL_LIBS_DIR}" -maxdepth 1 -type f -iname '*qlz*.aar' | wc -l | tr -d ' ')"
if [[ "${local_qlz_count}" != "1" ]]; then
  echo "[qlz-artifact][FAIL] expected exactly one local QLZ AAR, found ${local_qlz_count}" >&2
  exit 1
fi

only_local_qlz="$(find "${LOCAL_LIBS_DIR}" -maxdepth 1 -type f -iname '*qlz*.aar' -print -quit)"
if [[ "$(canonical_existing_path "${only_local_qlz}")" != "$(canonical_existing_path "${AAR_PATH}")" ]]; then
  echo "[qlz-artifact][FAIL] the configured QLZ AAR is not the sole approved local artifact" >&2
  exit 1
fi

actual_sha256="$(sha256_file "${AAR_PATH}")"
if [[ "${actual_sha256}" != "${APPROVED_AAR_SHA256}" ]]; then
  echo "[qlz-artifact][FAIL] QLZ AAR SHA-256 does not match the approved vendor artifact" >&2
  exit 1
fi

if ! unzip -tq "${AAR_PATH}" >/dev/null; then
  echo "[qlz-artifact][FAIL] approved QLZ AAR is not a valid ZIP archive" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
unzip -p "${AAR_PATH}" classes.jar > "${TMP_DIR}/classes.jar"
unzip -Z1 "${TMP_DIR}/classes.jar" > "${TMP_DIR}/class-entries.txt"

required_classes=(
  "com/evenmed/sdk/call/CheckConfig.class"
  "com/evenmed/sdk/call/CheckIml.class"
  "com/evenmed/sdk/call/SDKCall.class"
)

for required_class in "${required_classes[@]}"; do
  if ! grep -Fxq "${required_class}" "${TMP_DIR}/class-entries.txt"; then
    echo "[qlz-artifact][FAIL] approved QLZ AAR is missing required entry class: ${required_class}" >&2
    exit 1
  fi
done

echo "[qlz-artifact][PASS] approved QLZ AAR filename, SHA-256, uniqueness, ZIP structure, and entry classes are valid"

if [[ -z "${ARCHIVE_PATH}" ]]; then
  exit 0
fi

if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  echo "[qlz-artifact][FAIL] packaged APK/AAB is missing" >&2
  exit 1
fi
if ! unzip -tq "${ARCHIVE_PATH}" >/dev/null; then
  echo "[qlz-artifact][FAIL] packaged APK/AAB is not a valid ZIP archive" >&2
  exit 1
fi

dex_entries="$(unzip -Z1 "${ARCHIVE_PATH}" | grep -E '(^|/)classes[0-9]*\.dex$' || true)"
if [[ -z "${dex_entries}" ]]; then
  echo "[qlz-artifact][FAIL] packaged APK/AAB contains no DEX files" >&2
  exit 1
fi

required_descriptors=(
  "Lcom/evenmed/sdk/call/CheckConfig;"
  "Lcom/evenmed/sdk/call/CheckIml;"
  "Lcom/evenmed/sdk/call/SDKCall;"
)

for required_descriptor in "${required_descriptors[@]}"; do
  descriptor_found="false"
  dex_index=0
  while IFS= read -r dex_entry; do
    [[ -z "${dex_entry}" ]] && continue
    dex_index=$((dex_index + 1))
    extracted_dex="${TMP_DIR}/archive-${dex_index}.dex"
    unzip -p "${ARCHIVE_PATH}" "${dex_entry}" > "${extracted_dex}"
    if grep -aFq "${required_descriptor}" "${extracted_dex}"; then
      descriptor_found="true"
      break
    fi
  done <<< "${dex_entries}"

  if [[ "${descriptor_found}" != "true" ]]; then
    echo "[qlz-artifact][FAIL] packaged APK/AAB is missing required QLZ descriptor: ${required_descriptor}" >&2
    exit 1
  fi
done

echo "[qlz-artifact][PASS] packaged APK/AAB retains the required QLZ entry classes"
