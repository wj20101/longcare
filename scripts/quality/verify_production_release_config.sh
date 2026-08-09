#!/usr/bin/env bash
set -euo pipefail

production_requested="false"
acceptance_requested="false"
temporary_qlz_key_present="false"
qlz_test_mode="false"
known_unsafe_qlz_sdk_present="false"
known_unsafe_face_sdk_present="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --production-requested)
      production_requested="${2:-}"
      shift 2
      ;;
    --temporary-qlz-key-present)
      temporary_qlz_key_present="${2:-}"
      shift 2
      ;;
    --acceptance-requested)
      acceptance_requested="${2:-}"
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

if [[ "${production_requested}" == "true" && "${acceptance_requested}" == "true" ]]; then
  echo "[release-config][FAIL] release cannot be both production and acceptance" >&2
  exit 1
fi

if [[ "${production_requested}" != "true" ]]; then
  if [[ "${acceptance_requested}" != "true" ]]; then
    echo "[release-config][FAIL] non-production release requires explicit -Prelease.acceptance=true" >&2
    exit 1
  fi
  echo "[release-config][PASS] explicit acceptance build: temporary vendor configuration is allowed"
  exit 0
fi

violations=()
if [[ "${temporary_qlz_key_present}" == "true" ]]; then
  violations+=("QLZ SDK key is still the temporary fixed test key")
fi
if [[ "${qlz_test_mode}" == "true" ]]; then
  violations+=("QLZ SDK test mode is still enabled")
fi
if [[ "${known_unsafe_qlz_sdk_present}" == "true" ]]; then
  violations+=("QLZ SDK 1.3.0.2 contains a reachable weakened TLS trust manager")
fi
if [[ "${known_unsafe_face_sdk_present}" == "true" ]]; then
  violations+=("Tencent face SDK 6.6.2 contains ARM64 libraries without 16 KB ELF alignment")
fi

if [[ ${#violations[@]} -gt 0 ]]; then
  echo "[release-config][FAIL] production release configuration is not ready:" >&2
  for violation in "${violations[@]}"; do
    echo "- ${violation}" >&2
  done
  echo "Replace the flagged vendor SDKs and use server-supplied QLZ configuration before producing a release." >&2
  exit 1
fi

echo "[release-config][PASS] production release configuration is ready"
