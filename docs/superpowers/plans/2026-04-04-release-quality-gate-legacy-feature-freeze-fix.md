# Release Quality Gate Legacy Feature Freeze Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the two new legacy `app/features/**` Kotlin files by folding their tiny policy logic back into existing allowlisted files so the Android Release quality gate passes again.

**Architecture:** Keep the current runtime behavior unchanged and do not relax any quality rules. The fix is a structural rollback: inline each micro-policy into an existing allowlisted file in the same legacy package area, then delete the standalone file that triggered the freeze-rule failure.

**Tech Stack:** Kotlin, Android app module, GitHub Actions quality scripts, Gradle Kotlin compile tasks

---

### Task 1: Reproduce The Current Quality Gate Failure

**Files:**
- Verify: `scripts/quality/verify_architecture_boundaries.sh`
- Verify: `scripts/quality/run_quality_gate.sh`
- Verify: `.github/workflows/android-ci.yml`

- [ ] **Step 1: Run the architecture boundary check to capture the current failure**

Run:

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
```

Expected:

- Exit code `1`
- Output contains both of these paths as new files outside the legacy allowlist:

```text
app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt
app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt
```

- [ ] **Step 2: Run the unified quality gate with the same inputs used by CI**

Run:

```bash
bash scripts/quality/run_quality_gate.sh \
  --project-root . \
  --output-dir /tmp/quality-snapshot-local \
  --lint-report app/build/reports/lint-results-debug.txt \
  --source-root app/src/main/kotlin \
  --workflow-file .github/workflows/android-ci.yml
```

Expected:

- Exit code `1`
- `Architecture Boundaries` is the failing gate
- No signing, packaging, or release-only step is required to reproduce the failure

- [ ] **Step 3: Record the failure reason in the working notes before editing**

Use this note as the implementation guardrail:

```text
Do not edit the allowlist or the quality scripts.
Fix only by removing the two new standalone legacy files and preserving behavior.
```

### Task 2: Inline The Service Countdown Dispose Policy

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt`
- Verify: `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt`

- [ ] **Step 1: Add the dispose policy declarations into `ServiceCountdownLifecycleEffects.kt`**

Insert the local policy near the bottom of [ServiceCountdownLifecycleEffects.kt](/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt) so the only call site keeps working without cross-file indirection:

```kotlin
private data class ServiceCountdownDisposeActions(
    val cancelCountdownAlarm: Boolean = false,
    val stopAlarmRingtone: Boolean = false,
)

private fun resolveServiceCountdownDisposeActions(): ServiceCountdownDisposeActions {
    return ServiceCountdownDisposeActions()
}
```

Keep the existing `DisposableEffect(Unit)` usage unchanged:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        countdownViewModel.stopOrderStatePolling()

        val disposeActions = resolveServiceCountdownDisposeActions()
        if (disposeActions.cancelCountdownAlarm && latestCountdownState != ServiceCountdownState.ENDED) {
            countdownViewModel.cancelCountdownAlarm()
        }
        if (disposeActions.stopAlarmRingtone && latestCountdownState != ServiceCountdownState.ENDED) {
            AlarmRingtoneService.stopRingtone(context)
        }
    }
}
```

- [ ] **Step 2: Delete the standalone dispose policy file**

Run:

```bash
git rm app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownDisposePolicy.kt
```

Expected:

- The file is removed from the working tree
- No other file imports it, because the helper was file-local in practice

- [ ] **Step 3: Compile the app module after the inline move**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 4: Re-run the architecture boundary check and confirm the failure narrows**

Run:

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
```

Expected:

- Exit code `1`
- Only this remaining file is still reported:

```text
app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt
```

- [ ] **Step 5: Commit the service countdown inline fix**

Run:

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownLifecycleEffects.kt
git commit -m "refactor(servicecountdown): inline dispose policy"
```

Expected:

- One commit containing the lifecycle file update and the file deletion

### Task 3: Inline The Countdown Alarm Presentation Policy

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownNotificationUiDelegate.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt`
- Verify: `app/src/main/kotlin/com/ytone/longcare/features/countdown/service/AlarmRingtoneActivityLauncher.kt`

- [ ] **Step 1: Add the launch-source enum and presentation policy into `CountdownNotificationUiDelegate.kt`**

Insert the shared declarations near the top of [CountdownNotificationUiDelegate.kt](/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownNotificationUiDelegate.kt), after the imports and before the delegate class:

```kotlin
internal enum class CountdownAlarmLaunchSource {
    FULL_SCREEN_NOTIFICATION,
    DIRECT_SERVICE_LAUNCH,
}

internal object CountdownAlarmPresentationPolicy {
    fun autoCloseEnabled(launchSource: CountdownAlarmLaunchSource): Boolean {
        return when (launchSource) {
            CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION -> false
            CountdownAlarmLaunchSource.DIRECT_SERVICE_LAUNCH -> false
        }
    }
}
```

Do not change the behavior of the existing call inside the delegate:

```kotlin
autoCloseEnabled = CountdownAlarmPresentationPolicy.autoCloseEnabled(
    launchSource = CountdownAlarmLaunchSource.FULL_SCREEN_NOTIFICATION
)
```

- [ ] **Step 2: Delete the standalone presentation policy file**

Run:

```bash
git rm app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownAlarmPresentationPolicy.kt
```

Expected:

- The standalone file is removed
- The package path of the declarations stays the same, so the import in `AlarmRingtoneActivityLauncher.kt` remains valid

- [ ] **Step 3: Compile the app module after the second inline move**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 4: Re-run the architecture boundary check and confirm it passes**

Run:

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
```

Expected:

- Exit code `0`
- Output ends with:

```text
[architecture] boundary verification passed.
```

- [ ] **Step 5: Commit the countdown inline fix**

Run:

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/countdown/manager/CountdownNotificationUiDelegate.kt
git commit -m "refactor(countdown): inline alarm presentation policy"
```

Expected:

- One commit containing the delegate update and the file deletion

### Task 4: Run The Full Local Verification Used By CI

**Files:**
- Verify: `scripts/quality/run_quality_gate.sh`
- Verify: `scripts/quality/verify_architecture_boundaries.sh`
- Verify: `app/build/reports/lint-results-debug.txt`
- Verify: `/tmp/quality-snapshot-local/quality_snapshot.md`

- [ ] **Step 1: Run the unified quality gate end-to-end**

Run:

```bash
bash scripts/quality/run_quality_gate.sh \
  --project-root . \
  --output-dir /tmp/quality-snapshot-local \
  --lint-report app/build/reports/lint-results-debug.txt \
  --source-root app/src/main/kotlin \
  --workflow-file .github/workflows/android-ci.yml
```

Expected:

- Exit code `0`
- `Architecture Boundaries` reports `PASS`
- No other quality gate regresses

- [ ] **Step 2: Re-run the targeted compile check once more**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected:

- `BUILD SUCCESSFUL`

- [ ] **Step 3: Inspect the generated quality snapshot summary**

Run:

```bash
sed -n '1,220p' /tmp/quality-snapshot-local/quality_snapshot.md
```

Expected:

- The markdown report shows `PASS` for `Architecture Boundaries`
- There is no reference to new files outside `legacy_feature_files_allowlist.txt`

- [ ] **Step 4: Capture the final implementation commit**

Run:

```bash
git status --short
```

Expected:

- No modified or deleted Kotlin source files remain after the two refactor commits
- Untracked planning documents are acceptable if the implementation is happening in the same planning worktree

- [ ] **Step 5: Trigger the release workflow manually for the updated branch and inspect the next run**

Run:

```bash
git push
BRANCH=$(git branch --show-current)
gh workflow run android-release.yml --repo yyg20101/longcare --ref "$BRANCH" -f run_baseline_profile=false -f tx_face_sdk_source=local
gh run list --repo yyg20101/longcare --workflow "Android Release" --limit 5
```

Expected:

- The push succeeds for the current branch
- A new `Android Release` run appears for the updated ref
- The new run is available for follow-up inspection with `gh run view <run-id> --repo yyg20101/longcare`

Then inspect the next run with:

```bash
gh run list --repo yyg20101/longcare --workflow android-release.yml --limit 5
```

Expected:

- A new run appears for the updated commit
- The `Collect quality snapshot and enforce quality gates` step no longer fails on `Architecture Boundaries`
