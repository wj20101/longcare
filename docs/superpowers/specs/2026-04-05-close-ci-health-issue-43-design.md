# Close CI Health Issue 43 Design

## Background

Issue `#43` (`CI health threshold breach`) was originally opened and updated by the CI health monitor to track rolling CI health on `master`.

At the time the issue stayed open, that was correct because the sampled metrics were still below threshold even though some immediate CI failures had already been repaired.

Current confirmed state on `master`:

- Latest `Android CI` run succeeded:
  - `23990163815`
- Latest `Android Release` run succeeded:
  - `23971093894`
- A fresh manually triggered `CI Health Monitor` run succeeded:
  - `23990269403`

The latest CI health monitor report now states:

- `Rolling threshold status: PASS`
- `recent repair signal exists and rolling thresholds are currently healthy`

That means the rolling-sample breach tracked by `#43` has now actually recovered.

## Goal

Close issue `#43` with a short human-authored note referencing the fresh passing monitor result.

## Non-Goals

- Do not modify the CI health monitor workflow in this change.
- Do not change any thresholds.
- Do not reopen or rename the issue.
- Do not make any repository code changes.

## Options Considered

### Option 1: Close issue `#43` now, recommended

Add a short closing comment that references the latest passing monitor run, then close the issue.

Pros:

- Matches the current rolling-health truth.
- Keeps issue history accurate.
- Avoids leaving a stale breach issue open after recovery.

Cons:

- Requires one manual closure step because auto-close logic is not implemented.

### Option 2: Leave the issue open and wait for another scheduled monitor run

Pros:

- Most conservative option.

Cons:

- Leaves a stale open issue even though the latest rolling report is healthy.
- Adds noise for maintainers.

### Option 3: Close the issue without a closing note

Pros:

- Fastest.

Cons:

- Loses useful closure context.
- Makes it harder to understand why the issue was resolved.

## Decision

Use Option 1.

The latest monitor run provides sufficient evidence that the rolling CI health breach has recovered. The issue should be closed with a brief factual note.

## Design

### 1. Add a closing comment

Post a short comment to issue `#43` that references:

- latest `Android CI` success `23990163815`
- latest `Android Release` success `23971093894`
- latest `CI Health Monitor` success `23990269403`
- the monitor’s current `PASS` conclusion

### 2. Close the issue immediately after the comment

The issue should then be transitioned to `closed`.

### 3. Keep the note factual

The note should not over-explain. It only needs to record that rolling CI health has recovered and that the issue is being closed on that basis.

## Proposed Closing Comment

The implementation should post a comment equivalent to:

> Latest `master` signals are now healthy:
>
> - Android CI run `23990163815` succeeded
> - Android Release run `23971093894` succeeded
> - CI Health Monitor run `23990269403` succeeded and reported `Rolling threshold status: PASS`
>
> Closing this issue because the rolling CI health breach tracked by `#43` has recovered.

Exact wording may vary slightly, but the meaning should remain the same.

## Validation

The change is complete when:

- issue `#43` has a new closing comment
- issue `#43` is in `closed` state
- the comment references the latest successful CI and health-monitor runs

## Risks

### Risk: CI regresses again soon after closure

Mitigation:

- This is acceptable. The monitor can create or update a future issue if thresholds are breached again.

## Follow-up

If repeated manual closures become common, a future improvement can add safe auto-close behavior to the CI health monitor based on rolling threshold truth. That is out of scope here.
