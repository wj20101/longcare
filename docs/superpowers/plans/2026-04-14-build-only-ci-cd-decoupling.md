# Build-Only CI/CD Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all business-test execution from blocking CI/CD so compile, assemble, bundle, signing, and release packaging can proceed without business behavior assertions blocking the pipeline.

**Architecture:** Convert the existing Android CI, Android Release, and Face SDK migration workflows into build-only lanes. Keep workflow/governance and release-safety checks that validate pipeline integrity and packaging safety, but remove unit-test and business-regression coupling from workflow execution paths and helper scripts.

**Tech Stack:** GitHub Actions YAML, Bash helper scripts, Gradle task orchestration, Android app build tasks

---

## File Responsibility Map

- Modify: `.github/workflows/android-ci.yml`
  Purpose: PR/push debug build verification lane. Must become build-only and stop invoking unit tests or business smoke validation.

- Modify: `.github/workflows/android-release.yml`
  Purpose: release packaging lane. Must continue enforcing build/release safety but stop directly invoking business tests.

- Modify: `.github/workflows/face-sdk-migration-check.yml`
  Purpose: Tencent face SDK Maven-switch compatibility verification. Must remain compile-focused and not inherit business assertions.

- Modify: `.github/actions/android-build-env/action.yml`
  Purpose: shared setup action. Likely unchanged functionally, but confirm no business validation is injected implicitly.

- Modify: `scripts/quality/affected-modules.sh`
  Purpose: produces build verification task list for Android CI. Must emit build-only Gradle tasks.

- Modify: `scripts/face-sdk/verify_tx_face_maven_switch.sh`
  Purpose: build-only verification for Maven-switched face SDK dependency flow. Must stop running unit tests and non-build governance scripts.

- Modify: `scripts/quality/verify_ci_workflow_quality.sh`
  Purpose: workflow guard script. Must be updated so workflow expectations match the new build-only CI/CD design and do not require removed test-oriented behavior.

- Modify: `docs/architecture/ci-quality-gates.md`
  Purpose: stable documentation for what CI/CD enforces. Must reflect that blocking pipelines are build-only.

- Test via commands only:
  - `bash scripts/quality/affected-modules.sh --format text`
  - `bash scripts/quality/verify_ci_workflow_quality.sh`
  - `bash scripts/face-sdk/verify_tx_face_maven_switch.sh`
  - `gh workflow run "Android CI" --ref master`
  - `gh workflow run "Android Release" --ref master`

## Task 1: Make Android CI Build-Only

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/scripts/quality/affected-modules.sh`
- Modify: `/Users/wajie/StudioProjects/longcare/.github/workflows/android-ci.yml`

- [ ] **Step 1: Change affected-modules build task output to remove unit tests**

Update `scripts/quality/affected-modules.sh` so `verify_tasks` no longer includes `:app:testDebugUnitTest`.

Required task strings:

```bash
verify_tasks=":app:lintDebug :app:assembleDebug"
```

and for full scope:

```bash
verify_tasks=":app:lintDebug :app:assembleDebug :app:bundleDebug"
```

- [ ] **Step 2: Rename the Android CI verification step so it matches build-only behavior**

In `.github/workflows/android-ci.yml`, rename:

```yaml
- name: Run affected compile/lint/unit verification
```

to:

```yaml
- name: Run affected compile/lint/assemble verification
```

- [ ] **Step 3: Keep Android CI running only the build-only task list**

Ensure the step remains:

```yaml
run: ./gradlew --no-daemon ${{ needs.detect-affected.outputs.verify_tasks }}
```

and confirm the upstream script now emits only build tasks.

- [ ] **Step 4: Remove blocking instrumentation smoke execution from Android CI**

Delete the `instrumentation-smoke` job from `.github/workflows/android-ci.yml`.

If full deletion creates extra workflow guard churn, replace it with a non-blocking/manual-only stub in a follow-up task rather than keeping it in blocking CI.

- [ ] **Step 5: Run affected-modules locally to verify the new task output**

Run:

```bash
bash scripts/quality/affected-modules.sh --format text
```

Expected:
- output contains `:app:lintDebug :app:assembleDebug`
- output does not contain `:app:testDebugUnitTest`

- [ ] **Step 6: Commit the Android CI build-only changes**

```bash
git add scripts/quality/affected-modules.sh .github/workflows/android-ci.yml
git commit -m "refactor(ci): make android ci build only"
```

## Task 2: Make Android Release Build-and-Release-Only

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/.github/workflows/android-release.yml`

- [ ] **Step 1: Remove unit tests from the release pre-build verification step**

In `.github/workflows/android-release.yml`, change:

```yaml
run: ./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest
```

to:

```yaml
run: ./gradlew --no-daemon :app:lintDebug :app:assembleDebug
```

This keeps a build-integrity signal without invoking business tests.

- [ ] **Step 2: Keep the Android CI prerequisite check but treat it as build-only**

Do not remove the `Verify Android CI success for target commit` step.

Instead, rely on Task 1 so the prerequisite `Android CI` success now represents build-only success rather than business-test success.

- [ ] **Step 3: Verify release workflow still retains only release-safety gates**

Confirm these steps remain present:

```yaml
- name: Run release-required signing safety checks
- name: Run release-required keystore decode check
- name: Run release-required exported component guard
- name: Build release APK and AAB
```

and that no direct `:app:testDebugUnitTest` invocation remains anywhere in `android-release.yml`.

- [ ] **Step 4: Run workflow-quality verification locally**

Run:

```bash
bash scripts/quality/verify_ci_workflow_quality.sh
```

Expected:
- no failures caused by the Android Release build-step change

- [ ] **Step 5: Commit the Android Release build-only changes**

```bash
git add .github/workflows/android-release.yml
git commit -m "refactor(release): remove business tests from release lane"
```

## Task 3: Make Face SDK Migration Check Compile-Only

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/scripts/face-sdk/verify_tx_face_maven_switch.sh`
- Modify: `/Users/wajie/StudioProjects/longcare/.github/workflows/face-sdk-migration-check.yml`

- [ ] **Step 1: Remove unit tests from the face-sdk build invocation**

In `scripts/face-sdk/verify_tx_face_maven_switch.sh`, change:

```bash
./gradlew --no-daemon :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest :app:processReleaseMainManifest \
```

to:

```bash
./gradlew --no-daemon :app:compileDebugKotlin :app:lintDebug :app:processReleaseMainManifest :app:assembleDebug \
```

The added `:app:assembleDebug` keeps this lane compile/package oriented without reintroducing business tests.

- [ ] **Step 2: Remove non-build governance checks from the face-sdk script**

Delete these lines from `scripts/face-sdk/verify_tx_face_maven_switch.sh`:

```bash
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
bash scripts/quality/verify_cancellation_guards.sh app/src/main/kotlin
bash scripts/quality/verify_no_empty_catch_blocks.sh app/src/main/kotlin
bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts .github/workflows/android-ci.yml
bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml
```

Keep:

```bash
bash scripts/quality/verify_release_exported_components.sh
```

only if you decide exported component safety still belongs in this specialized compile lane. If that is still considered too policy-heavy, remove it as well and keep the script purely compile/manifest based.

- [ ] **Step 3: Keep the workflow wrapper simple**

In `.github/workflows/face-sdk-migration-check.yml`, keep the workflow structure but do not add any new business or policy checks. The workflow should remain a thin wrapper around the simplified build-only script.

- [ ] **Step 4: Run the face-sdk verification script locally**

Run:

```bash
bash scripts/face-sdk/verify_tx_face_maven_switch.sh
```

Expected:
- local AAR publish succeeds
- Maven-switch compile/lint/manifest/assemble succeeds
- script no longer fails on business tests or governance-only gates

- [ ] **Step 5: Commit the face-sdk build-only changes**

```bash
git add scripts/face-sdk/verify_tx_face_maven_switch.sh .github/workflows/face-sdk-migration-check.yml
git commit -m "refactor(ci): make face sdk migration build only"
```

## Task 4: Update Workflow Quality Guard Expectations

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/scripts/quality/verify_ci_workflow_quality.sh`

- [ ] **Step 1: Remove assumptions that Android CI or Android Release run unit tests**

Search `scripts/quality/verify_ci_workflow_quality.sh` for checks that implicitly rely on:

- `Run affected compile/lint/unit verification`
- `:app:testDebugUnitTest`
- instrumentation-smoke job presence

Update them to reflect the new build-only intent.

- [ ] **Step 2: Make the step-name expectations match the new build-only names**

If the guard checks the Android CI step label, update it from:

```bash
Run affected compile/lint/unit verification
```

to:

```bash
Run affected compile/lint/assemble verification
```

- [ ] **Step 3: Remove required checks for instrumentation-smoke if that job was deleted**

If Task 1 removed `instrumentation-smoke`, remove the corresponding guard requirements from `verify_ci_workflow_quality.sh`.

- [ ] **Step 4: Re-run workflow-quality verification**

Run:

```bash
bash scripts/quality/verify_ci_workflow_quality.sh
```

Expected:
- verification passes with the new build-only workflow design

- [ ] **Step 5: Commit the workflow guard updates**

```bash
git add scripts/quality/verify_ci_workflow_quality.sh
git commit -m "refactor(ci): align workflow guards with build-only lanes"
```

## Task 5: Update Stable CI/CD Documentation

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/docs/architecture/ci-quality-gates.md`

- [ ] **Step 1: Document that blocking CI/CD is now build-only**

Add or update prose in `docs/architecture/ci-quality-gates.md` to say:

- blocking CI/CD validates buildability and release safety
- business tests and business-rule validation are no longer part of blocking CI/CD

- [ ] **Step 2: Document the new responsibilities of the three workflows**

Add short sections or a comparison table for:

- `Android CI`
- `Android Release`
- `Face SDK Migration Check`

showing each workflow’s new build-only scope.

- [ ] **Step 3: Explicitly list what no longer blocks packaging**

Add a short section stating that these no longer block CI/CD:

- unit tests
- UI behavior assertions
- business regression checks
- business journey checks

- [ ] **Step 4: Commit the documentation update**

```bash
git add docs/architecture/ci-quality-gates.md
git commit -m "docs(ci): document build-only pipeline boundaries"
```

## Task 6: End-to-End Verification

**Files:**
- Verify only

- [ ] **Step 1: Re-run local build-only Android CI equivalent**

Run:

```bash
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
```

Expected:
- build succeeds without any test execution requirement

- [ ] **Step 2: Re-run local release build equivalent**

Run:

```bash
./gradlew --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

Expected:
- release packaging tasks complete, subject only to local signing configuration availability

- [ ] **Step 3: Re-run workflow guard**

Run:

```bash
bash scripts/quality/verify_ci_workflow_quality.sh
```

Expected:
- pass

- [ ] **Step 4: Push branch or current master changes**

```bash
git push origin master
```

Expected:
- remote updated with the build-only CI/CD refactor

- [ ] **Step 5: Trigger Android CI manually**

Run:

```bash
gh workflow run "Android CI" --ref master
```

Expected:
- workflow triggers successfully

- [ ] **Step 6: Confirm Android CI no longer executes unit tests**

Run:

```bash
gh run list --workflow "Android CI" --branch master --limit 1
```

Then inspect the latest run and confirm:
- no `:app:testDebugUnitTest` invocation remains in the blocking lane

- [ ] **Step 7: Trigger Android Release after Android CI succeeds**

Run:

```bash
gh workflow run "Android Release" --ref master
```

Expected:
- release can progress as long as build-only Android CI is green

- [ ] **Step 8: Confirm a UI/business-only change no longer blocks packaging**

Validation rule:
- if a future UI/business assertion changes without breaking compilation or packaging, Android CI and Android Release should remain runnable

Use the workflow logs from Steps 5-7 to confirm the blocking chain no longer depends on business tests.

- [ ] **Step 9: Final commit if any verification-driven edits were needed**

```bash
git status --short
```

If changes were made during verification, commit them with a scoped message before closing out.

## Spec Coverage Check

- Build-only Android CI: covered by Task 1
- Build-only Android Release: covered by Task 2
- Compile-only face-sdk migration lane: covered by Task 3
- Workflow quality guard alignment: covered by Task 4
- Stable documentation update: covered by Task 5
- End-to-end workflow validation: covered by Task 6

## Placeholder Scan

No `TODO`, `TBD`, or deferred placeholders remain in this plan. All file paths and commands are explicit.

## Type / Naming Consistency Check

- The plan consistently uses the names:
  - `Run affected compile/lint/assemble verification`
  - `Android CI`
  - `Android Release`
  - `Face SDK Migration Check`
- The Gradle task sets are consistently build-only across the plan.
