# GitHub Actions Cache Cleanup Design

Date: 2026-04-28

## Context

GitHub Actions caches are being created by CI, mainly through the shared Android build environment action that uses `gradle/actions/setup-gradle@v5`. Current repository cache data shows Gradle cache entries such as:

- `gradle-dependencies-v1-*`
- `gradle-home-v1|Linux-X64|...`
- `gradle-wrapper-zips-v1-*`
- `gradle-java-toolchains-v1-*`

The repository already has `.github/workflows/actions-runs-cleanup.yml`, but that workflow deletes old workflow runs only. It does not delete Actions caches. As CI runs continue, cache entries can accumulate toward GitHub's 10GB repository cache limit.

## Goals

- Automatically delete old GitHub Actions cache entries after workflows that create caches.
- Keep recently created and recently accessed caches so current CI performance remains stable.
- Avoid relying only on a daily cleanup schedule.
- Keep manual dry-run support for safe inspection.
- Publish clear cleanup summaries in GitHub Actions.

## Non-Goals

- Do not disable Gradle caching.
- Do not change Gradle build logic.
- Do not delete workflow run history as part of the cache cleanup change.
- Do not require third-party marketplace actions for cache deletion.

## Recommended Approach

Add a repository-local cache cleanup script and run it from a follow-up cleanup job after cache-producing jobs complete.

This matches the desired behavior: when CI creates cache entries, the same workflow run also checks repository cache pressure and removes old entries if needed.

Important implementation detail: `gradle/actions/setup-gradle` saves caches in post-action cleanup, near the end of the job. A cleanup step inside the same job can run before the new cache is visible. Therefore, cleanup should run in a separate job that `needs` the build job.

## Workflow Design

### Android CI

Add a `cleanup-caches` job to `.github/workflows/android-ci.yml`.

- `needs: verify-build`
- `if: always()`
- `permissions: actions: write, contents: read`
- Runs after the build job completes, so newly saved caches are visible.
- Executes the shared cleanup script with defaults.

### Android Release

Add a `cleanup-caches` job to `.github/workflows/android-release.yml`.

- `needs: release-build`
- `if: always()`
- Same permissions and script defaults.

### Baseline Profile

Add a `cleanup-caches` job to `.github/workflows/baseline-profile.yml`.

- `needs: generate-baseline-profile`
- `if: always()`
- Same permissions and script defaults.

### Existing Actions Runs Cleanup

Keep `.github/workflows/actions-runs-cleanup.yml` focused on workflow run retention. Optionally add a manual cache cleanup path later, but this design does not require mixing run cleanup and cache cleanup in the same workflow.

## Cleanup Script Design

Create `scripts/quality/cleanup_github_actions_caches.sh`.

Inputs:

- `--repo OWNER/REPO`
- `--max-total-mb N`, default `7168`
- `--keep-recent-days N`, default `2`
- `--dry-run`, default false for CI jobs

Environment:

- `GH_TOKEN` must have `actions: write`.

Behavior:

1. Query all caches with `gh api --paginate repos/${REPO}/actions/caches`.
2. Sum `size_in_bytes`.
3. If total size is less than or equal to `max-total-mb`, delete nothing.
4. Build deletion candidates ordered by `last_accessed_at` oldest first.
5. Exclude caches whose `created_at` or `last_accessed_at` is within `keep-recent-days`.
6. Delete candidates until total size is below the threshold or no candidates remain.
7. Print a concise summary and append it to `GITHUB_STEP_SUMMARY` when available.

The script should be defensive:

- Validate numeric inputs.
- Refuse to run without `GH_TOKEN`.
- Support dry-run output that lists candidate IDs, keys, refs, sizes, and timestamps.
- Continue deleting independent candidates even if one delete call fails, but report failures and exit non-zero if any deletion failed.

## Default Policy

Default policy:

- Maximum total cache size: `7168 MB`
- Protect caches created or accessed in the last `2 days`
- Delete oldest last-accessed caches first

This keeps roughly 3GB of headroom before GitHub's 10GB limit while protecting fresh Gradle caches.

## Error Handling

- If cache listing fails, fail the cleanup job but do not fail the build job's result because cleanup runs as a separate `if: always()` job.
- If deletion partially fails, report the failed IDs in the summary.
- If no candidates are safe to delete and cache is still above threshold, emit a warning summary.

## Testing

Local/script validation:

- Shell syntax check with `bash -n`.
- Dry-run mode against the current repository.
- Verify summary output includes total size, threshold, scanned count, candidate count, deleted count, and reclaimed size.

Workflow validation:

- Run `scripts/quality/verify_ci_workflow_quality.sh`.
- Trigger Android CI or use workflow_dispatch when available and confirm `cleanup-caches` runs after cache-producing jobs.

## Rollout

1. Add the script.
2. Add `cleanup-caches` jobs to cache-producing workflows.
3. Run dry-run manually if needed.
4. Let scheduled or normal CI validate automatic cleanup behavior.

## Risks

- Deleting too aggressively may slow subsequent CI runs.
  - Mitigation: keep recent cache protection and use a 7GB threshold instead of deleting all old caches.
- Newly saved caches may not be visible immediately.
  - Mitigation: run cleanup in a separate job after build job completion.
- GitHub API shape may change.
  - Mitigation: keep script small, use `gh api` fields already exposed by the Actions cache endpoint, and fail with clear diagnostics.
