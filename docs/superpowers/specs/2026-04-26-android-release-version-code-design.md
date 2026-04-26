# Android Release Version Code Auto-Increment Design

Date: 2026-04-26

## Context

`constants.gradle.kts` currently stores a fixed Android `appVersionCode`:

```kotlin
val appVersionCode by extra(29)
```

`.github/workflows/android-release.yml` reads that value during release, then uses it for release APK/AAB build metadata and artifact names. Because the value is static, repeated release workflow runs can produce packages with the same `versionCode`.

## Goal

When `android-release` runs, increment `appVersionCode`, commit the change, push it, and build the release package with the new version code.

The built APK/AAB and the repository state after release must agree on the same incremented `appVersionCode`.

## Non-Goals

- Do not auto-increment `appVersionName`.
- Do not move version metadata out of `constants.gradle.kts`.
- Do not change Gradle version wiring in `app/build.gradle.kts`.
- Do not commit unrelated files from the runner workspace.
- Do not support tag-triggered auto-bump in this iteration, because a tag points to the old commit while the release build would be produced from a newly created commit.

## Recommended Behavior

Use a pre-build bump for `workflow_dispatch` releases:

1. Checkout the release branch with push credentials and full enough history.
2. Reject tag-triggered releases for this auto-bump flow, or require manual branch dispatch instead.
3. Parse the current `appVersionCode` from `constants.gradle.kts`.
4. Compute `nextVersionCode = current + 1`.
5. Replace only the `appVersionCode` line.
6. Commit as GitHub Actions bot:
   - `chore(release): bump app version code to <nextVersionCode>`
7. Push the commit back to the current branch.
8. Continue release checks and build from the bumped workspace.
9. Extract version metadata after the bump so artifact names and GitHub Release metadata use the new code.

## Trigger Policy

`workflow_dispatch` is the supported release path for auto-incrementing version code.

For `push.tags: v*`, the workflow should not auto-bump and build from a newly created commit, because the tag would still reference the previous commit. To avoid confusing release provenance, tag-triggered runs should fail early with a clear message telling maintainers to use manual `workflow_dispatch` release, or tag releases should be redesigned later to create the tag after the bump commit.

## Workflow Changes

### Checkout

Update checkout so the workflow can push the bump commit:

- Use `fetch-depth: 0` or enough history for push safety.
- Use the default `GITHUB_TOKEN`, with existing `contents: write` permission.

### Early Tag Guard

Add an early step after checkout:

```bash
if [ "${GITHUB_EVENT_NAME}" = "push" ] && [[ "${GITHUB_REF}" == refs/tags/* ]]; then
  echo "::error::VersionCode auto-bump is only supported for workflow_dispatch branch releases. Use workflow_dispatch instead of tag push."
  exit 1
fi
```

### Bump Version Code

Add a pre-build step before version metadata extraction and before release build:

```bash
set -euo pipefail

CURRENT_VERSION_CODE="$(sed -nE 's/.*appVersionCode by extra\(([0-9]+)\).*/\1/p' constants.gradle.kts | head -n1)"
if [ -z "${CURRENT_VERSION_CODE}" ]; then
  echo "::error::Failed to parse appVersionCode from constants.gradle.kts"
  exit 1
fi

NEXT_VERSION_CODE="$((CURRENT_VERSION_CODE + 1))"
sed -i -E "s/val appVersionCode by extra\([0-9]+\)/val appVersionCode by extra(${NEXT_VERSION_CODE})/" constants.gradle.kts

if ! git diff -- constants.gradle.kts | grep -q "appVersionCode"; then
  echo "::error::appVersionCode was not changed"
  exit 1
fi

git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add constants.gradle.kts
git commit -m "chore(release): bump app version code to ${NEXT_VERSION_CODE}"
git push
```

The exact command should preserve the current Kotlin syntax:

```kotlin
val appVersionCode by extra(<number>)
```

### Metadata Extraction

Keep the existing `Extract app version metadata` step after the bump. It should read the newly committed value and expose the incremented code through `steps.app_version.outputs.version_code`.

## Concurrency And Race Handling

Keep the existing workflow concurrency group. This prevents duplicate runs on the same ref from racing each other.

For branch releases, if another commit lands before `git push`, the push should fail rather than force-push. Maintainers can rerun release on the latest branch state.

## Verification

Automated verification:

- Run a local shell syntax check for the inserted workflow scripts where practical.
- Add no broad Gradle changes; existing release build remains the main integration verification.

Manual release verification:

1. Run `android-release` via `workflow_dispatch` on the target branch.
2. Confirm the workflow creates and pushes a commit that changes only `constants.gradle.kts`.
3. Confirm `appVersionCode` is incremented by exactly 1.
4. Confirm `Extract app version metadata` reports the new code.
5. Confirm APK/AAB names include the new code.
6. Confirm GitHub Release metadata includes the new code.

## Risks

- If release is triggered by tag push, tag provenance is ambiguous. This is why tag-triggered auto-bump is explicitly blocked.
- If the branch receives a newer commit during the release run, the version bump push can fail. This is safer than force-pushing.
- GitHub Actions bot commits can retrigger CI. That is acceptable and keeps the bumped version visible in normal branch history.
