#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-.}"
PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"

if [[ ! -d "${PROJECT_ROOT}/.git" ]]; then
  echo "[keystore-guard][FAIL] git repository not found at ${PROJECT_ROOT}" >&2
  exit 1
fi

tracked_keystores="$({ git -C "${PROJECT_ROOT}" ls-files | grep -E '\.(jks|keystore)$'; } || true)"

if [[ -n "${tracked_keystores}" ]]; then
  echo "[keystore-guard][FAIL] tracked keystore files detected:" >&2
  printf '%s\n' "${tracked_keystores}" >&2
  echo "[keystore-guard][FAIL] remove them from git and rotate secrets." >&2
  exit 1
fi

echo "[keystore-guard] no tracked keystore files found."
