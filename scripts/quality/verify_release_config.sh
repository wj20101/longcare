#!/usr/bin/env bash
set -euo pipefail

temporary_qlz_key_present="false"
qlz_test_mode="false"
known_unsafe_qlz_sdk_present="false"
known_unsafe_face_sdk_present="false"

while [[ $# -gt 0 ]]; do
  if [[ $# -lt 2 || ( "$2" != "true" && "$2" != "false" ) ]]; then
    echo "[release-config][FAIL] each option requires true or false" >&2
    exit 2
  fi
  case "$1" in
    --temporary-qlz-key-present)
      temporary_qlz_key_present="${2:-}"
      shift 2
      ;;
    --qlz-test-mode)
      qlz_test_mode="${2:-}"
      shift 2
      ;;
    --known-unsafe-qlz-sdk-present)
      known_unsafe_qlz_sdk_present="${2:-}"
      shift 2
      ;;
    --known-unsafe-face-sdk-present)
      known_unsafe_face_sdk_present="${2:-}"
      shift 2
      ;;
    *)
      echo "[release-config][FAIL] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

violations=()
if [[ "${temporary_qlz_key_present}" == "true" ]]; then
  violations+=("QLZ SDK key is still the temporary fixed test key")
fi
if [[ "${qlz_test_mode}" == "true" ]]; then
  violations+=("QLZ SDK test mode is still enabled")
fi
if [[ "${known_unsafe_qlz_sdk_present}" == "true" ]]; then
  violations+=("QLZ SDK 1.3.0.5 contains a reachable weakened TLS trust manager")
fi
if [[ "${known_unsafe_face_sdk_present}" == "true" ]]; then
  violations+=("Tencent face SDK 6.6.2 contains ARM64 libraries without 16 KB ELF alignment")
fi

if [[ ${#violations[@]} -gt 0 ]]; then
  # Explicitly accepted for the current vendor/configuration baseline on 2026-09-19.
  # Keep the risk visible; this is not evidence that the SDK issues are fixed.
  echo "[release-config][WARN] approved vendor/configuration risks remain:" >&2
  for violation in "${violations[@]}"; do
    echo "- ${violation}" >&2
  done
  echo "[release-config][WARN] approved baseline is unchanged; vendor security/device compatibility is not guaranteed." >&2
fi

echo "[release-config][PASS] configuration risks checked; signing and quality checks remain required"
