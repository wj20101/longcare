# Lint Waiver Drift Cleanup Design

## Background

After the CI/CD governance work, local release-oriented verification still fails at the lint waiver allowlist step.

Current failing command path:

- `bash scripts/quality/run_quality_gate.sh ...`
- `bash scripts/quality/preflight_local.sh --release`

The failing check is:

- `scripts/lint/verify_lint_warning_allowlist.sh`

The current failure is not caused by new lint issues. It is caused by stale waiver entries that are no longer present in the current lint report.

Observed stale waiver IDs:

- `GradleDependency`
- `NewerVersionAvailable`

Current lint report still contains warnings for:

- `Aligned16KB`
- `GlobalOptionInConsumerRules`
- `TrustAllX509TrustManager`

## Goal

Restore local release-oriented quality checks by removing stale lint waiver entries and documenting the intended strictness split between local enforcement and CI enforcement.

## Non-Goals

- Do not redesign the entire lint governance system.
- Do not relax local stale-waiver enforcement.
- Do not add new waiver IDs in this change.
- Do not change the current active waivers that still correspond to real warnings.

## Options Considered

### Option 1: Remove stale waivers and document the local/CI enforcement split

Delete the two stale waiver entries and add a small clarification about why stale waivers fail locally by default but are advisory in CI unless explicitly tightened.

Pros:

- Fixes the current release-oriented local gate.
- Preserves strict local hygiene.
- Reduces future confusion about why local and CI behavior differ.

Cons:

- Does not eliminate future waiver drift by itself.

### Option 2: Minimal cleanup only

Delete the two stale waiver entries and change nothing else.

Pros:

- Smallest possible patch.

Cons:

- Leaves the policy split under-documented.
- Future developers can hit the same confusion again.

### Option 3: Loosen stale-waiver enforcement

Make local runs advisory for stale waivers too.

Pros:

- Lowest friction for local developers.

Cons:

- Weakens the usefulness of local preflight.
- Encourages waiver drift to accumulate.
- Conflicts with the governance direction of catching avoidable CI issues earlier.

## Decision

Use Option 1.

This is the narrowest fix that resolves the current blocker while reinforcing the newly introduced governance model instead of weakening it.

## Design

### 1. Remove the two stale waiver entries

Update:

- `scripts/lint/lint_warning_waivers.json`

Remove only these entries:

- `GradleDependency`
- `NewerVersionAvailable`

Keep the remaining entries untouched:

- `Aligned16KB`
- `GlobalOptionInConsumerRules`
- `TrustAllX509TrustManager`

### 2. Preserve current enforcement semantics

Do not change the core behavior of:

- `scripts/lint/verify_lint_warning_allowlist.sh`

The intended behavior remains:

- local/default execution: stale waivers are blocking
- CI auto mode: stale waivers are advisory unless `LINT_ENFORCE_UNUSED_WAIVERS=true`

### 3. Clarify the enforcement split in documentation

Update the CI quality gate documentation, likely:

- `docs/architecture/ci-quality-gates.md`

Add a short clarification near the lint-waiver section or local-preflight assumptions:

- local runs fail on stale waivers by default
- CI auto mode treats stale waivers as non-blocking to avoid noisy red pipelines
- CI can be made strict by explicitly setting `LINT_ENFORCE_UNUSED_WAIVERS=true`

This should explain the policy without expanding scope into a full lint redesign.

## Files Expected To Change

- `scripts/lint/lint_warning_waivers.json`
- `docs/architecture/ci-quality-gates.md`

## Validation

The change is complete when all of the following pass:

- `bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt`
- `bash scripts/quality/run_quality_gate.sh --project-root . --output-dir /tmp/quality-snapshot-local --lint-report app/build/reports/lint-results-debug.txt --source-root app/src/main/kotlin --workflow-file .github/workflows/android-ci.yml`
- `bash scripts/quality/preflight_local.sh --release`

Success criteria:

- `Lint Warning Allowlist` no longer fails because of stale `GradleDependency` and `NewerVersionAvailable` waiver entries
- local release-oriented preflight can progress beyond the waiver-drift blocker
- documentation explains the intended local vs CI stale-waiver behavior

## Risks

### Risk: a supposedly stale waiver is still needed in another lint context

Mitigation:

- Validate against the current `app/build/reports/lint-results-debug.txt`
- Remove only entries that are confirmed absent from the current report

### Risk: developers misread the local/CI strictness difference as inconsistency

Mitigation:

- Document the split explicitly in the quality gate guide
- Keep the explanation short and tied to the actual environment variable behavior

## Follow-up

If waiver drift continues to recur, a later improvement can add a dedicated stale-waiver maintenance command or a more visible lint-waiver inventory report. That is intentionally out of scope for this cleanup.
