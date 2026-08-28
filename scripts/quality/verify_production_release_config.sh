#!/usr/bin/env bash
set -euo pipefail

production_requested=""
acceptance_requested=""
qlz_key_present=""
known_test_qlz_key_requested=""
qlz_test_mode=""
approved_qlz_aar_present=""
approved_qlz_aar_hash_matches=""
known_unsafe_face_sdk_present=""

fail_missing_value() {
  echo "[release-config][FAIL] missing value for $1" >&2
  exit 2
}

read_value() {
  [[ $# -ge 2 && -n "${2:-}" ]] || fail_missing_value "$1"
  printf '%s' "$2"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --production-requested)
      production_requested="$(read_value "$@")"
      shift 2
      ;;
    --acceptance-requested)
      acceptance_requested="$(read_value "$@")"
      shift 2
      ;;
    --qlz-key-present)
      qlz_key_present="$(read_value "$@")"
      shift 2
      ;;
    --known-test-qlz-key-requested)
      known_test_qlz_key_requested="$(read_value "$@")"
      shift 2
      ;;
    --qlz-test-mode)
      qlz_test_mode="$(read_value "$@")"
      shift 2
      ;;
    --approved-qlz-aar-present)
      approved_qlz_aar_present="$(read_value "$@")"
      shift 2
      ;;
    --approved-qlz-aar-hash-matches)
      approved_qlz_aar_hash_matches="$(read_value "$@")"
      shift 2
      ;;
    --known-unsafe-face-sdk-present)
      known_unsafe_face_sdk_present="$(read_value "$@")"
      shift 2
      ;;
    *)
      echo "[release-config][FAIL] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

validate_boolean() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "[release-config][FAIL] ${name} must be true or false" >&2
    exit 2
  fi
}

validate_boolean "production_requested" "${production_requested}"
validate_boolean "acceptance_requested" "${acceptance_requested}"
validate_boolean "qlz_key_present" "${qlz_key_present}"
validate_boolean "known_test_qlz_key_requested" "${known_test_qlz_key_requested}"
validate_boolean "qlz_test_mode" "${qlz_test_mode}"
validate_boolean "approved_qlz_aar_present" "${approved_qlz_aar_present}"
validate_boolean "approved_qlz_aar_hash_matches" "${approved_qlz_aar_hash_matches}"
validate_boolean "known_unsafe_face_sdk_present" "${known_unsafe_face_sdk_present}"

if [[ "${production_requested}" == "true" && "${acceptance_requested}" == "true" ]]; then
  echo "[release-config][FAIL] release cannot be both production and acceptance" >&2
  exit 1
fi

if [[ "${production_requested}" != "true" && "${acceptance_requested}" != "true" ]]; then
  echo "[release-config][FAIL] non-production release requires explicit -Prelease.acceptance=true" >&2
  exit 1
fi

violations=()

if [[ "${qlz_key_present}" != "true" ]]; then
  violations+=("QLZ SDK key is missing")
fi
if [[ "${approved_qlz_aar_present}" != "true" ]]; then
  violations+=("approved QLZ AAR is missing")
elif [[ "${approved_qlz_aar_hash_matches}" != "true" ]]; then
  violations+=("QLZ AAR SHA-256 does not match the approved vendor artifact")
fi

if [[ "${production_requested}" == "true" ]]; then
  if [[ "${known_test_qlz_key_requested}" == "true" ]]; then
    violations+=("QLZ SDK key is the known test credential")
  fi
  if [[ "${qlz_test_mode}" == "true" ]]; then
    violations+=("QLZ SDK test mode was requested for production")
  fi
  if [[ "${known_unsafe_face_sdk_present}" == "true" ]]; then
    violations+=("Tencent face SDK 6.6.2 contains ARM64 libraries without 16 KB ELF alignment")
  fi
else
  if [[ "${qlz_test_mode}" != "true" ]]; then
    violations+=("acceptance release requires explicit QLZ test mode")
  fi
fi

if [[ ${#violations[@]} -gt 0 ]]; then
  echo "[release-config][FAIL] release configuration is not ready:" >&2
  for violation in "${violations[@]}"; do
    echo "- ${violation}" >&2
  done
  echo "Provide the required project-controlled configuration and approved artifacts; diagnostics never print credential values." >&2
  exit 1
fi

if [[ "${production_requested}" == "true" ]]; then
  echo "[release-config][PASS] production project-controlled configuration is ready"
else
  echo "[release-config][PASS] explicit acceptance configuration is ready"
fi
