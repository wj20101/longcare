#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

changed_files="$({ git diff --name-only --diff-filter=ACMRTUXB HEAD; git ls-files --others --exclude-standard; } | awk 'NF' | sort -u)"
if [[ -z "${changed_files}" ]]; then
  echo "[real-device-scope][PASS] no working-tree changes to inspect."
  exit 0
fi

allowed='^(scripts/quality/|openspec/changes/complete-real-device-acceptance-evidence/|docs/architecture/(system-overview|ci-quality-gates|tech-stack|roadmap-and-open-gaps)\.md$|\.github/workflows/(android-ci|android-release|baseline-profile)\.yml$)'
protected='(^|/)(libs?|vendor)/.*\.(aar|jar)$|\.(aar|jar)$|consumer-rules|^gradle/libs\.versions\.toml$|^settings\.gradle\.kts$|^constants\.gradle\.kts$|(^|/)build\.gradle\.kts$|^gradle\.properties$|^(app|core|feature|baselineprofile)/|AndroidManifest\.xml$'
failures=0
while IFS= read -r path; do
  if [[ "${path}" =~ ${protected} ]]; then
    echo "[real-device-scope][FAIL] protected Android/vendor/build input changed: ${path}" >&2
    failures=$((failures + 1))
  elif [[ ! "${path}" =~ ${allowed} ]]; then
    echo "[real-device-scope][FAIL] path is outside the approved acceptance-tooling scope: ${path}" >&2
    failures=$((failures + 1))
  fi
done <<< "${changed_files}"

if [[ "${failures}" -ne 0 ]]; then
  exit 1
fi
echo "[real-device-scope][PASS] diff is limited to acceptance tooling, its OpenSpec change, governed workflows, and four architecture documents; protected AAR/JAR, SDK, dependency, app, navigation, WebView, database, and startup inputs are unchanged."
