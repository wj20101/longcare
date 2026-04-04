# CI Health Issue 43 Follow-up Design

## Background

Open GitHub issue `#43` (`CI health threshold breach`) is maintained by the scheduled workflow:

- `.github/workflows/ci-health-monitor.yml`

That workflow creates or updates the issue whenever CI health thresholds are breached. It does not automatically close the issue when conditions improve.

Recent repository context:

- PR `#44` (`fix: restore Android Release quality gate`) has already been merged.
- The repaired release workflow run succeeded:
  - `Android Release` run `23969631822`
- The latest `master` `Android CI` run also succeeded:
  - `Android CI` run `23970826638`

However, the health issue tracks rolling CI health over a sample window, not just the latest run. Because of that, one new success does not guarantee that the thresholds in the health report are already back within bounds.

## Goal

Update issue `#43` with a human-authored status comment that records the repair progress and keeps the issue open until the CI health monitor sample actually recovers above threshold.

## Non-Goals

- Do not close issue `#43` in this change.
- Do not modify the CI health monitor workflow.
- Do not alter threshold configuration.
- Do not implement Node.js 20 warning mitigation in this change.

## Options Considered

### Option 1: Add a manual status comment and keep the issue open

Post a concise comment to `#43` that references the merged fix and the successful validation runs, while explicitly noting that the issue remains open until the monitor’s sampled metrics recover.

Pros:

- Matches the current monitoring design.
- Avoids prematurely closing a still-valid tracking issue.
- Gives maintainers and bots a clear human checkpoint in the timeline.

Cons:

- The issue remains open for now.

### Option 2: Trigger the CI health monitor immediately, then decide whether to close

Manually run the `CI Health Monitor` workflow and use its new output as the decision point.

Pros:

- Gives a fresh report before acting.

Cons:

- Still likely to fail threshold evaluation if the sample window is dominated by earlier failures.
- Adds another step before the simple status update the team needs now.

### Option 3: Close the issue now because the main failure was fixed

Close `#43` immediately and rely on the bot to reopen or recreate a future issue if health remains poor.

Pros:

- Fastest resolution.

Cons:

- Misrepresents the current state if thresholds are still breached.
- Conflicts with the issue’s purpose as a rolling-health tracker.

## Decision

Use Option 1.

The issue should remain open until the health monitor itself reflects recovery. This follow-up should document the progress already made without overstating the current CI health state.

## Design

### 1. Add a human status comment to issue `#43`

Post one concise comment to:

- `https://github.com/yyg20101/longcare/issues/43`

The comment should include:

- PR `#44` has been merged
- The `Android Release` failure chain has been repaired
- The repaired release validation run succeeded
- The latest `master` `Android CI` run succeeded
- The issue is intentionally kept open until the rolling CI health sample recovers above threshold

### 2. Do not close the issue

Even with recent successful runs, the existing health monitor report may still be correct for the sampled window. Closing the issue before the next health evaluation would introduce ambiguity and may create bot churn.

### 3. Keep the comment factual and time-bounded

The comment should avoid claiming that CI health is fully restored. It should only state:

- what was fixed
- what evidence now exists
- why the issue remains open

## Proposed Comment Shape

The implementation should post a comment equivalent to this:

> PR #44 has been merged and the release-quality-gate failure path has been repaired.
>
> Validation completed with:
> - Android Release run `23969631822` succeeded
> - latest `master` Android CI run `23970826638` succeeded
>
> Keeping this issue open for now because `#43` tracks rolling CI health over the monitor sample window, not just the latest successful run. Once the sampled metrics recover above threshold, this issue can be closed.

Exact wording may be adjusted for clarity, but the meaning should remain unchanged.

## Validation

The change is complete when:

- issue `#43` has a new manual comment
- the comment accurately references PR `#44`
- the comment accurately references successful runs `23969631822` and `23970826638`
- issue `#43` remains open after the update

## Risks

### Risk: the comment becomes stale if CI regresses again quickly

Mitigation:

- Keep the comment narrow and factual.
- Avoid forecasting future health beyond the cited runs.

### Risk: maintainers may assume the issue is ready to close

Mitigation:

- State explicitly that the issue remains open until the rolling sample recovers.

## Follow-up

After the next `CI Health Monitor` run posts a fresh report, reassess whether `#43` still reflects an active threshold breach. If the sampled metrics recover, the issue can then be closed with a short closing note.
