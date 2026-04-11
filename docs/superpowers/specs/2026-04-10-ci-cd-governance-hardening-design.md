# CI/CD Governance Hardening Design

## Background

Recent CI/CD failures show that the current Android pipeline is functional but still vulnerable to governance drift:

- advisory lint/version warnings can fail `Android CI` when waiver metadata drifts
- some failures are first discovered on `master` or during manual release rather than earlier
- failure logs contain useful information, but the path from failure to remediation is still more manual than it should be

The current system already has strong building blocks:

- `preflight_local.sh`
- `Android CI`
- `Android Release`
- `collect_quality_snapshot.sh`
- lint waiver metadata

The problem is not a lack of checks. The problem is that the ownership and layer boundaries of those checks are not explicit enough, and the diagnostics are not yet standardized enough to make failures cheap to resolve.

## Goal

Reduce future CI/CD failures by systematizing governance around:

- gate layering
- rule metadata
- failure diagnostics
- release preconditions

without redesigning the entire pipeline from scratch.

## Non-Goals

- No business-feature changes.
- No full replacement of existing shell scripts or workflows.
- No immediate migration to a completely new “unified CI engine”.
- No broad Android build-logic refactor unrelated to governance stability.

## Options Considered

### Option 1: Layered governance hardening on top of the current pipeline

Keep the current local/CI/release structure, but make the layer responsibilities explicit, add a gate registry, standardize diagnostics, and tighten release preconditions.

Pros:

- Builds on proven existing workflows.
- Lowest migration risk.
- Fastest path to reducing repeat failures.
- Makes future failures easier to diagnose and assign.

Cons:

- Some duplication across scripts will still remain.

### Option 2: Centralized quality-gate engine

Replace current script/workflow orchestration with a single source-driven engine that determines all gates and execution layers.

Pros:

- Cleanest long-term architecture.

Cons:

- Much larger refactor.
- Higher rollout risk.
- Easy to destabilize working CI in the name of cleanup.

### Option 3: Release-first hardening only

Keep most of the current system unchanged and focus on making `Android Release` stricter and more defensive.

Pros:

- Smallest scope.
- Immediate improvement to release confidence.

Cons:

- Does not solve earlier-stage CI drift.
- Allows avoidable failures to continue showing up too late.

## Decision

Use Option 1.

This is the best balance between immediate stability improvement and controlled change size. The existing workflow structure is already good enough to harden; the missing piece is stronger governance around how checks are described, layered, and surfaced.

## Design

### 1. Formalize gate layering through a registry

Introduce a machine-readable gate registry, for example:

- `scripts/quality/quality_gate_registry.json`

Each gate entry should define at least:

- `id`
- `name`
- `layer`
  - `local-fast`
  - `ci-required`
  - `release-required`
  - `observability-only`
- `owner`
- `source_of_truth`
- `likely_fix`
- `blocking`

Rationale:

- The project already has de facto gate layering in docs and scripts.
- A registry makes that layering explicit and inspectable.
- It reduces hidden coupling between bash scripts, workflow YAML, and docs.

### 2. Keep existing runners, but align them to registry semantics

Do not replace the current scripts immediately.

Instead:

- `preflight_local.sh` remains the local orchestrator
- `Android CI` remains the merge-blocking CI workflow
- `Android Release` remains the release workflow
- `run_quality_gate.sh` / `collect_quality_snapshot.sh` remain the snapshot/release-oriented gate entrypoints

But each should more clearly operate against the same declared gate model.

Expected effect:

- local checks fail earlier on locally-actionable governance drift
- CI remains the canonical blocker for `ci-required`
- release no longer acts as a second generic CI pass by accident

### 3. Standardize failure diagnostics

Gate failures should emit a normalized summary shape wherever possible:

- failed gate ID / name
- layer
- owner
- source of truth
- likely fix

This can be implemented incrementally by enhancing output in:

- `collect_quality_snapshot.sh`
- `verify_lint_warning_allowlist.sh`
- `preflight_local.sh`

Rationale:

- The logs already contain enough signal, but not enough standard structure.
- Standardized diagnostics reduce repair time and ownership ambiguity.

### 4. Treat drift-prone governance rules as managed metadata

Some rules are more failure-prone than others, especially:

- lint waivers
- source allowlists
- review deadlines
- advisory warnings vs blocking warnings

These should be governed as metadata, not implied policy.

For lint waivers specifically:

- keep `scripts/lint/lint_warning_waivers.json`
- align its shape and semantics with the broader gate-governance model
- make advisory-vs-blocking intent explicit in docs and failure output

Rationale:

- The recent failure was caused by governance metadata drift, not a code defect.
- This category should be first-class in the governance design.

### 5. Tighten Android Release preconditions

`Android Release` should remain responsible for release-only confidence and artifact publication, but it should not be the first place ordinary governance problems surface.

Recommended hardening:

- require that `ci-required` validation has already passed for the target commit or branch state
- make that precondition explicit in workflow output
- fail early with a clear message if release is being run on an unverified commit

This can be implemented as:

- a pre-release check against recent `Android CI` status for the same commit, or
- a smaller local invariant if GitHub status lookup is intentionally avoided

Rationale:

- Release should confirm readiness, not rediscover basic governance drift.

### 6. Preserve incremental rollout

This governance hardening should be rolled out incrementally:

1. Add gate registry
2. Align docs and failure messages
3. Tighten `preflight_local.sh`
4. Tighten `Android CI`
5. Add release precondition guard

Rationale:

- This keeps the system continuously usable.
- It reduces the risk of introducing instability while trying to improve stability.

## Files Expected To Change

- `scripts/quality/quality_gate_registry.json` (new)
- `scripts/quality/preflight_local.sh`
- `scripts/quality/run_quality_gate.sh`
- `scripts/quality/collect_quality_snapshot.sh`
- `scripts/lint/verify_lint_warning_allowlist.sh`
- `scripts/lint/lint_warning_waivers.json`
- `.github/actions/android-build-env/action.yml`
- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`
- `docs/architecture/ci-quality-gates.md`
- optionally `conductor/workflow.md`

## Validation

This design is successfully implemented only if all of the following become true:

- a gate can be traced to a declared layer and owner
- local preflight output clearly shows which failures are local-fast vs CI vs release concerns
- `Android CI` failures surface a more direct remediation path
- `Android Release` does not proceed on an obviously unverified commit state
- documentation and scripts no longer drift on gate semantics

## Risks

### Risk: governance metadata becomes yet another layer to maintain

Mitigation:

- keep the registry minimal
- use it to clarify high-value gates first, not every script in the repo at once

### Risk: release precondition checks become brittle or over-constrained

Mitigation:

- start with a narrow precondition rule
- keep failure messaging explicit and override-friendly if needed

### Risk: local preflight becomes too heavy

Mitigation:

- preserve existing mode split (`local-fast`, `--full`, `--release`)
- add clarity before adding more work

## Follow-up

If the registry-based approach proves effective, a later phase can centralize more gate execution logic around the registry. That deeper unification is intentionally out of scope for this design.
