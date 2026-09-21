#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_REF_TYPE:-}" == branch ]]
[[ "$(git rev-parse HEAD)" == "$GITHUB_SHA" ]] || { echo 'Source commit changed.' >&2; exit 1; }
source scripts/quality/gradle_constants.sh
version="$(read_gradle_extra_value constants.gradle.kts appVersionCode)"
name="$(read_gradle_extra_value constants.gradle.kts appVersionName)"
tag="v${name}-${version}"
# Never replace an already published version, even on a workflow rerun.
if git ls-remote --exit-code --tags origin "refs/tags/$tag"; then
  echo "Tag $tag already exists; refusing to replace it." >&2
  exit 1
else
  status=$?
  [[ "$status" == 2 ]] || exit "$status"
fi
while IFS= read -r file; do
  case "$file" in
    constants.gradle.kts|docs/architecture/tech-stack.md) ;;
    *) echo "Unexpected release change: $file" >&2; exit 1 ;;
  esac
done < <({ git diff --name-only HEAD; git ls-files --others --exclude-standard; } | sort -u)
git diff --exit-code HEAD -- constants.gradle.kts >/dev/null && { echo 'Version was not changed.' >&2; exit 1; }
git config user.name 'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
git add constants.gradle.kts docs/architecture/tech-stack.md
git commit -m "chore(release): bump app version code to $version"
git push origin "HEAD:refs/heads/$GITHUB_REF_NAME"
echo "commit_sha=$(git rev-parse HEAD)" >> "$GITHUB_OUTPUT"
