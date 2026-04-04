# Android Release Quality Gate Fix Design

## Background

The GitHub Actions run `23968220912` for the `Android Release` workflow failed in the `Collect quality snapshot and enforce quality gates` step.

The failing gate is `Architecture Boundaries`, reproduced locally with:

- `bash scripts/quality/verify_architecture_boundaries.sh .`

The direct cause is not a runtime or release-build defect. The failure comes from the legacy `app/features/**` freeze rule, which forbids adding new Kotlin files under that directory tree.

Two newly added files violate that rule:

- `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt`

## Goal

Restore the release quality gate to green without weakening architecture rules and without changing runtime behavior.

## Non-Goals

- No module migration in this change.
- No allowlist expansion in `scripts/quality/legacy_feature_files_allowlist.txt`.
- No change to the quality gate scripts.
- No behavioral change to countdown alarm presentation or service countdown dispose handling.

## Options Considered

### Option 1: Inline the new micro-policies into existing allowlisted files

Delete the two new files and move their contents into already-allowlisted files in the same legacy area.

Pros:

- Fixes CI immediately.
- Preserves the legacy freeze rule as-is.
- Keeps behavior unchanged.
- Smallest and lowest-risk patch.

Cons:

- Does not advance the longer-term migration out of `app/features/**`.

### Option 2: Add the new files to the legacy allowlist

Expand `scripts/quality/legacy_feature_files_allowlist.txt` to admit the two new files.

Pros:

- Fastest possible fix.

Cons:

- Weakens the freeze rule.
- Makes future cleanup harder by normalizing continued growth in the legacy area.

### Option 3: Move the new files into `feature/servicecountdown`

Relocate the new policies into a non-legacy module.

Pros:

- Best long-term architectural direction.

Cons:

- Larger change than needed for this CI repair.
- Risks turning a focused release fix into a broader migration task.

## Decision

Use Option 1.

This change should stay narrowly focused on restoring the release quality gate while preserving the rule that caused the failure. The two violating files are both very small policy wrappers, so folding them back into existing allowlisted files is the lowest-maintenance near-term fix.

## Design

### 1. Remove `ServiceCountdownDisposePolicy.kt`

Delete:

- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt`

Move these definitions into:

- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt`

Definitions to inline:

- `ServiceCountdownDisposeActions`
- `resolveServiceCountdownDisposeActions()`

Rationale:

- These definitions are only used by `ServiceCountdownLifecycleEffects.kt`.
- The target file is still comfortably sized, so the inline move does not create a new file-size problem.
- Keeping the policy near its only call site improves local readability after the standalone file is removed.

### 2. Remove `CountdownAlarmPresentationPolicy.kt`

Delete:

- `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt`

Move these definitions into:

- `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownNotificationUiDelegate.kt`

Definitions to inline:

- `CountdownAlarmLaunchSource`
- `CountdownAlarmPresentationPolicy`

Rationale:

- One of the current call sites already lives in `CountdownNotificationUiDelegate.kt`.
- The other call site in `AlarmRingtoneActivityLauncher.kt` can keep importing the moved symbols from the same package.
- This preserves package-level visibility while eliminating the extra frozen-file violation.

### 3. Preserve runtime behavior exactly

The moved logic must keep the same results as today:

- `resolveServiceCountdownDisposeActions()` continues to return the default `ServiceCountdownDisposeActions()` instance.
- `CountdownAlarmPresentationPolicy.autoCloseEnabled(...)` continues to return `false` for both:
  - `FULL_SCREEN_NOTIFICATION`
  - `DIRECT_SERVICE_LAUNCH`

No caller behavior should change as part of this fix.

## Files Expected To Change

- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownNotificationUiDelegate.kt`
- Delete `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt`
- Delete `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt`

## Validation

The change is complete only if all of the following pass:

- `bash scripts/quality/verify_architecture_boundaries.sh .`
- `bash scripts/quality/run_quality_gate.sh --project-root . --output-dir /tmp/quality-snapshot-local --lint-report app/build/reports/lint-results-debug.txt --source-root app/src/main/kotlin --workflow-file .github/workflows/android-ci.yml`
- Relevant Kotlin compile targets for touched code

Success criteria:

- `Architecture Boundaries` no longer reports new files outside the frozen legacy allowlist.
- The release quality gate no longer fails on this issue.
- No runtime behavior changes are introduced.

## Risks

### Risk: helper types become less discoverable after inlining

Mitigation:

- Keep the moved declarations close to their current usage sites.
- Preserve naming so call sites remain readable.

### Risk: future work may reintroduce new legacy files

Mitigation:

- Do not expand the allowlist in this change.
- Let the existing gate keep protecting the frozen legacy directory.

## Follow-up

If countdown and service countdown continue to evolve, a later dedicated migration can move these policies into non-legacy feature modules. That follow-up is intentionally out of scope for this CI repair.
