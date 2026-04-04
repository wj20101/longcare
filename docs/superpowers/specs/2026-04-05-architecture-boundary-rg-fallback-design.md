# Architecture Boundary Ripgrep Fallback Design

## Background

PR `#45` currently fails in GitHub Actions on the `Architecture Boundaries` check inside the quality snapshot phase.

Observed failing run:

- `Android CI` run `23983979320`

The failure is not caused by an actual architecture rule violation in the codebase. The failure log shows that the CI environment running the check does not have `rg` available:

```text
rg: command not found
```

Because `scripts/quality/verify_architecture_boundaries.sh` now intentionally fails hard when its scanner fails, the check correctly avoids a false green, but it also means the script is currently not portable enough for this CI environment.

## Goal

Make `scripts/quality/verify_architecture_boundaries.sh` work correctly both when `rg` is available and when it is not, without reintroducing false-green behavior.

## Non-Goals

- Do not weaken any architecture rules.
- Do not modify allowlists or budgets.
- Do not move scanner installation into GitHub Actions as the primary fix.
- Do not broaden this into a larger CI environment provisioning change.

## Options Considered

### Option 1: Add a grep fallback inside the boundary script

Make the script prefer `rg`, but fall back to `grep -R -n -E` when `rg` is unavailable.

Pros:

- Keeps the script self-contained.
- Works locally and in CI without additional environment assumptions.
- Fits the governance goal of reducing hidden CI-only dependencies.

Cons:

- Requires careful scanner wrapper logic so semantics stay correct.

### Option 2: Install ripgrep in CI

Update shared CI setup to ensure `rg` exists before the architecture script runs.

Pros:

- Centralized environment fix.

Cons:

- Reintroduces a hidden environment dependency.
- Makes the script less portable outside GitHub Actions.

### Option 3: Do both

Install `rg` in CI and also add a fallback.

Pros:

- Highest redundancy.

Cons:

- More moving parts than needed for this bug.

## Decision

Use Option 1.

The boundary script should not depend on CI-specific tool provisioning to function. Adding an internal fallback preserves strictness while improving portability and reducing future maintenance friction.

## Design

### 1. Replace `run_rg_scan` with a scanner wrapper that supports fallback

Update:

- `scripts/quality/verify_architecture_boundaries.sh`

Current behavior:

- always tries `rg`
- treats scanner failure as a hard error

Desired behavior:

- if `rg` is available, use `rg`
- otherwise use `grep`
- if the active scanner itself fails unexpectedly, still fail hard with diagnostics

### 2. Preserve current success/failure semantics

The fallback must keep the same three logical outcomes:

1. **match found**
   - return data to the caller
   - caller treats it as a rule violation

2. **no match**
   - caller treats it as pass for that specific rule

3. **scanner execution failure**
   - caller treats it as a hard failure
   - no false green

### 3. Apply fallback consistently across all architecture scan helpers

The fallback must cover all scan entrypoints used by:

- `run_rule`
- `run_allowlisted_rule`
- `run_filtered_rule`

There should not be one path that uses fallback and another that still hard-depends on `rg`.

### 4. Keep diagnostics explicit

If scanner execution fails, the script should still emit clear diagnostics such as:

- which rule failed
- which backend was being used (`rg` or `grep`)
- exit code
- stderr output when available

### 5. Avoid changing rule definitions

This is an execution-environment compatibility fix only. Do not change:

- regex patterns
- allowlist logic
- file threshold logic
- architecture rule set

## Files Expected To Change

- `scripts/quality/verify_architecture_boundaries.sh`

## Validation

The change is complete when all of the following are true:

- `bash scripts/quality/verify_architecture_boundaries.sh .` passes locally
- PR `#45` no longer fails because `rg` is missing
- the script still fails correctly on real rule violations
- the script does not silently pass if the scanner backend fails unexpectedly

## Risks

### Risk: grep fallback behaves differently from rg for some patterns

Mitigation:

- Use a single wrapper and keep the no-match / match / hard-failure contract explicit
- Limit the change to scanner selection and execution handling

### Risk: grep recursion or file filtering differs from rg

Mitigation:

- Preserve directory scoping from the existing callers
- Restrict grep to Kotlin files only, matching current intent

## Follow-up

If more quality scripts are later found to depend implicitly on `rg`, a separate follow-up can standardize a shared shell helper for scanner selection. That is out of scope for this targeted fix.
