# Build-Only CI/CD Decoupling Design

## Background

The current CI/CD chain mixes build verification, workflow governance, release safety, and business-level behavior checks into the same blocking path.

That coupling has created a failure mode that does not match the project’s delivery needs:

- a UI display policy or business assertion can block `Android CI`
- `Android Release` depends on a green `Android CI`
- therefore a business/UI regression test can block normal compile, package, and release execution

This is especially problematic for cases where the codebase still compiles, lints, signs, and packages correctly, but a business assertion changes or a UI behavior test becomes stale.

The user’s requirement is explicit:

- CI/CD should no longer run business validation as part of blocking build/release flows
- business logic changes must not prevent normal compile and packaging tasks from completing
- build and packaging pipelines should only be gated by build integrity, workflow integrity, and release safety checks

## Goal

Refactor the existing GitHub Actions and helper scripts so that blocking CI/CD becomes **build-only**.

After this change:

- `Android CI` proves the project can compile and assemble debug artifacts
- `Android Release` proves the project can safely assemble and package release artifacts
- `Face SDK Migration Check` proves the Maven-switch path still compiles and resolves correctly
- business tests, UI behavior tests, and business-rule validation no longer participate in blocking CI/CD

## Non-Goals

- Do not redesign business tests themselves
- Do not delete business tests from the repository
- Do not remove all engineering governance from CI/CD
- Do not weaken release signing or exported-component safety checks
- Do not introduce a large new testing framework or a broad CI platform rewrite

## Current Problem Areas

### 1. Android CI mixes build verification and business testing

`android-ci.yml` currently runs affected verification tasks that include:

- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- optionally `:app:bundleDebug`

This means a business-facing unit test failure prevents the CI build lane from going green, even if debug assembly works.

### 2. Android Release is transitively blocked by business test failures

`android-release.yml` requires a successful `Android CI` run for the same commit before release can proceed.

That dependency would be acceptable if `Android CI` were build-only. It is not acceptable while `Android CI` still includes business tests.

`android-release.yml` also directly runs:

- `:app:lintDebug`
- `:app:testDebugUnitTest`

So release is currently blocked both transitively and directly by business-level tests.

### 3. Face SDK migration verification is overloaded

`verify_tx_face_maven_switch.sh` currently does three things:

1. publishes local Tencent face AARs to `mavenLocal`
2. runs compile/lint/test verification
3. runs additional repository governance checks

That script therefore acts as:

- dependency migration verification
- business test lane
- repository policy lane

Those responsibilities should be separated.

## Design Principles

### Principle 1: Build pipelines validate buildability, not business intent

Blocking CI/CD should answer:

- can Gradle resolve the project?
- can the project compile?
- can the project assemble/package artifacts?
- can release signing and release packaging succeed?

Blocking CI/CD should not answer:

- is this UI rule still desirable?
- does this business assertion still hold?
- did a product behavior test change?

### Principle 2: Release should depend only on build-safe prerequisites

Release may still depend on:

- successful build-only CI for the same commit
- workflow correctness
- signing/keystore validation
- release artifact generation
- explicit release-safety guards

Release should not depend on business tests.

### Principle 3: Specialized compatibility workflows should validate compatibility only

The face SDK migration lane should answer:

- can the project switch to Maven-sourced Tencent face dependencies?
- can it compile and process required manifests under that configuration?

It should not also act as a business regression suite.

## Proposed Architecture

### A. Android CI becomes build-only

`android-ci.yml` should keep:

- `detect-affected`
- workflow governance checks
- low-cost engineering guardrails that are not business assertions
- `lintDebug`
- `assembleDebug`
- optional `bundleDebug` for full-scope changes
- artifact/report upload

`android-ci.yml` should remove:

- `:app:testDebugUnitTest`
- any blocking instrumentation or business smoke validation

Result:

- CI remains a real build confidence signal
- UI/business regressions no longer block debug packaging

### B. Android Release becomes build-and-release-only

`android-release.yml` should keep:

- prerequisite check for a successful build-only `Android CI` run on the same commit
- workflow governance checks
- lint if desired as a build/static-integrity signal
- signing safety checks
- keystore decode check
- release exported component guard
- `assembleRelease`
- `bundleRelease`
- artifact renaming, checksums, mapping archive, upload, and publish

`android-release.yml` should remove:

- `:app:testDebugUnitTest`
- business or UI behavior checks
- business journey checks

Result:

- release remains safe
- release is no longer blocked by business tests

### C. Face SDK migration check becomes compile-only

`verify_tx_face_maven_switch.sh` should keep:

- publish local AARs to `mavenLocal`
- Maven-switch build invocation
- enough verification to prove dependency resolution and build correctness

Recommended retained Gradle tasks:

- `:app:compileDebugKotlin`
- `:app:lintDebug`
- `:app:processReleaseMainManifest`
- optionally `:app:assembleDebug` if a stronger build signal is wanted

`verify_tx_face_maven_switch.sh` should remove:

- `:app:testDebugUnitTest`
- lint warning allowlist enforcement
- target SDK policy verification
- exact alarm permission config verification
- cancellation / empty-catch governance checks

Result:

- the workflow remains useful for dependency migration validation
- it no longer gets blocked by unrelated business assertions or repository policy rules

## Concrete File-Level Changes

### 1. `scripts/quality/affected-modules.sh`

Current partial verify task set:

- `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug`

Current full verify task set:

- `:app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:bundleDebug :baselineprofile:assemble`

Proposed partial verify task set:

- `:app:lintDebug :app:assembleDebug`

Proposed full verify task set:

- `:app:lintDebug :app:assembleDebug :app:bundleDebug`

Also remove build-lane coupling to instrumentation decisions if they no longer affect blocking CI.

### 2. `.github/workflows/android-ci.yml`

Change `verify-build` so that:

- `Run affected compile/lint/unit verification` becomes build-only verification
- the step name should be updated to reflect that it no longer runs tests

Recommended replacement wording:

- `Run affected compile/lint/assemble verification`

If `instrumentation-smoke` remains, it should be converted to:

- non-blocking
- manual-only
- or removed entirely from CI/CD

Preferred outcome:

- remove it from blocking CI/CD

### 3. `.github/workflows/android-release.yml`

Change the build step from:

- `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest`

to a build-only variant, for example:

- `./gradlew --no-daemon :app:lintDebug :app:assembleDebug`

or simply:

- `./gradlew --no-daemon :app:lintDebug`

The release lane already runs:

- `:app:assembleRelease`
- `:app:bundleRelease`

so the pre-release compile step should remain minimal and non-business.

### 4. `scripts/face-sdk/verify_tx_face_maven_switch.sh`

Change the core verification command from:

- `:app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest :app:processReleaseMainManifest`

to:

- `:app:compileDebugKotlin :app:lintDebug :app:processReleaseMainManifest`

Optionally:

- add `:app:assembleDebug`

Then remove the trailing repository-policy commands:

- `verify_lint_warning_allowlist.sh`
- `verify_cancellation_guards.sh`
- `verify_no_empty_catch_blocks.sh`
- `verify_target_sdk_upgrade.sh`
- `verify_exact_alarm_permission_config.sh`

Keep only checks that are required to prove the face SDK switch still builds correctly.

### 5. `.github/workflows/face-sdk-migration-check.yml`

No large structural rewrite is required, but its intent should become:

- compile-only compatibility validation

The workflow may still retain:

- workflow quality guard
- shared environment setup

But it should not reintroduce business validation indirectly via the script.

## What Stays in Blocking CI/CD

The following are still acceptable as blocking checks because they verify build/release integrity rather than business behavior:

- `verify_ci_workflow_quality.sh`
- `verify_gradle_stability.sh`
- `verify_lint_ignore_policy.sh` if treated as engineering configuration validation rather than product logic
- `verify_module_dependency_whitelist.sh`
- `verify_release_exported_components.sh`
- signing / keystore checks
- compile / lint / assemble / bundle tasks

## What Leaves Blocking CI/CD

These should no longer block CI/CD:

- `:app:testDebugUnitTest`
- UI behavior tests
- business logic regression tests
- business workflow smoke assertions
- baseline profile journey verification
- lint warning allowlist enforcement for advisory/version-driven warnings
- policy scripts that validate product behavior rather than buildability

## Operational Impact

### Positive Impact

- packaging and release flows become stable and predictable
- business/UI test churn no longer prevents delivery
- release failures become more actionable because they correlate with actual release/build issues

### Trade-Off

- CI/CD will no longer catch business regressions automatically

This is acceptable under the current requirement because the user explicitly prioritizes reliable compile/package/release execution over automatic business validation in CI/CD.

## Rollout Strategy

1. Convert `affected-modules.sh` to build-only task output
2. Update `android-ci.yml` build verification step names and commands
3. Update `android-release.yml` to remove unit-test dependency
4. Simplify `verify_tx_face_maven_switch.sh`
5. Re-run:
   - `Android CI`
   - `Face SDK Migration Check`
   - `Android Release`
6. Confirm a UI-only behavior change no longer blocks packaging

## Acceptance Criteria

This design is successful when:

- `Android CI` can go green without running `:app:testDebugUnitTest`
- `Android Release` is no longer blocked by business/UI unit tests
- `Face SDK Migration Check` validates compile compatibility without running business tests
- a business/UI-only assertion change does not block normal compile and packaging workflows
- release still fails when actual build, signing, or release packaging is broken

## Risks

### Risk: business regressions become invisible

Mitigation:

- acceptable under current requirement
- optional future follow-up can move business regression checks into manual or advisory workflows

### Risk: some retained guards are still too policy-heavy

Mitigation:

- keep the implementation narrow first
- if a retained guard still behaves like business policy, remove it in a follow-up pass

## Recommendation

Proceed with the build-only CI/CD split now.

The current coupling is causing delivery friction that is disproportionate to its value. The release chain should be optimized for buildability and release safety, not blocked by business assertions or UI display logic.
