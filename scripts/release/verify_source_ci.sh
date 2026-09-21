#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REF_TYPE:-}" == branch ]] || { echo 'Release requires a branch.' >&2; exit 1; }
run="$(gh run list --workflow android-ci.yml --commit "$GITHUB_SHA" --branch "$GITHUB_REF_NAME" \
  --limit 1 --json databaseId,status,conclusion,headSha)"
id="$(jq -er --arg sha "$GITHUB_SHA" '.[0] | select(.headSha == $sha and .status == "completed" and .conclusion == "success") | .databaseId' <<< "$run")" || {
  echo 'No successful Android CI for the source commit. Run Android CI first.' >&2
  exit 1
}
# A docs-only green run is not build evidence. Manual CI always runs the build and smoke.
gh run view "$id" --json jobs | jq -e '[.jobs[] | select(.name == "verify-build" and .conclusion == "success")] | length == 1' >/dev/null
