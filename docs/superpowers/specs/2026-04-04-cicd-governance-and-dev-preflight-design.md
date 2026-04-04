# CI/CD Governance And Developer Preflight Design

## Background

The current project CI/CD system has grown into a layered set of workflows, shared actions, shell guards, and health-monitor automation:

- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`
- `.github/workflows/baseline-profile.yml`
- `.github/workflows/ci-health-monitor.yml`
- `.github/actions/android-build-env/action.yml`
- `scripts/quality/*`

This structure is functional, but it has an important maintenance problem: developers can make seemingly simple changes, such as adding a new Kotlin file or splitting a class, and only discover hidden CI constraints after pushing or opening a PR.

Recent evidence:

- A new legacy `app/features/**` file triggered the architecture freeze rule and broke release quality gates.
- The failing rule was technically correct, but the developer experience was poor because the “why” and “how to fix” were not visible until CI feedback was read in detail.
- CI health monitoring can report trend problems, but it does not help prevent these issues from reaching CI in the first place.

## Problem Statement

The repository currently lacks a clear and unified developer-facing “preflight” layer. Quality rules exist, but they are distributed across multiple scripts and workflows, some are effectively hidden, and failure messages are not always optimized for fast diagnosis.

This creates three ongoing costs:

1. **Late failure discovery** — developers learn about certain rule violations only after CI runs.
2. **Rule opacity** — repository-specific constraints such as frozen directories or file budgets are enforced, but not surfaced as first-class developer guidance.
3. **Governance sprawl** — responsibilities are split across shared workflow setup, workflow-local steps, shell scripts, and CI health tracking without a strong visible taxonomy.

## Goal

Create a durable CI/CD governance model that:

- helps developers catch the most common CI failures locally before push
- makes repository-specific rules explicit and understandable
- reduces maintenance overhead by clarifying which checks belong to local development, PR CI, release gating, and health monitoring
- improves failure output so remediation is fast and obvious

## Non-Goals

- Do not remove or weaken architecture boundaries, frozen-directory rules, or release gates.
- Do not attempt a full migration away from all legacy `app/features/**` code in this initiative.
- Do not redesign every workflow from scratch.
- Do not collapse all checks into one monolithic script.

## Options Considered

### Option 1: Full-chain governance with phased rollout

Add a local preflight layer, classify existing quality gates by enforcement tier, refactor workflow responsibilities for clarity, and improve CI health tracking as a trend system rather than a first-alert system.

Pros:

- Best long-term reduction in maintenance cost
- Addresses both developer experience and CI governance
- Preserves existing safeguards while making them easier to live with

Cons:

- Larger initiative than a single bugfix
- Must be implemented in stages to control risk

### Option 2: Developer-experience-only fix

Add only local preflight scripts and documentation, but leave CI structure mostly unchanged.

Pros:

- Fastest reduction in day-to-day developer friction

Cons:

- Leaves workflow duplication and governance ambiguity in place
- Maintenance burden remains elevated

### Option 3: CI-only cleanup

Refactor workflows and quality scripts for better layering, but do not add a stronger local developer preflight story.

Pros:

- Improves pipeline clarity

Cons:

- Still leaves developers discovering failures too late

## Decision

Use Option 1 with staged implementation.

This is the only option that directly addresses the full maintenance problem: developers need a clear local preflight experience, CI needs clearer layering, and health monitoring needs to act as operational trend feedback rather than the first place where process problems are discovered.

## Design Overview

The solution is divided into three phases:

- **Phase A: Local visibility and preflight**
- **Phase B: CI quality-layer reorganization**
- **Phase C: CI health monitor refinement**

Each phase produces value independently, but they reinforce each other.

## Phase A: Local Visibility And Preflight

### Objective

Catch the most common CI-breaking mistakes locally before push, especially repository-specific constraints that are not obvious from normal Android or Kotlin development conventions.

### New Entry Point

Add a unified local preflight command, for example:

- `scripts/quality/preflight_local.sh`

This command becomes the main developer-facing quality entrypoint.

### Responsibilities

The local preflight should:

- run the most relevant fast local checks
- expose repository-specific hidden constraints as first-class developer feedback
- support different execution scopes, such as:
  - fast/default
  - changed-only
  - full
  - release-oriented

### New Focused Guard

Add a dedicated new-file guard, for example:

- `scripts/quality/check_new_files_guard.sh`

Its purpose is to detect high-friction cases like:

- new Kotlin files added inside frozen legacy directories
- new files that should have gone to `feature/*` modules
- new files that trigger allowlist-based governance rules

### Output Expectations

The guard must not only fail. It must explain:

- which file triggered the problem
- which rule was violated
- where the source-of-truth rule file lives
- what the recommended fix path is

Example categories:

- `Frozen legacy directory`
- `Allowlist violation`
- `Architecture boundary violation`
- `File size threshold`

### Documentation

Add a dedicated document, for example:

- `docs/architecture/ci-quality-gates.md`

This document should list:

- each quality script
- what it checks
- which enforcement tier it belongs to
- when developers should run it
- how to resolve common failures

## Phase B: CI Quality Layer Reorganization

### Objective

Reduce governance sprawl by clarifying where checks belong and why they run.

### Enforcement Tiers

Classify quality gates into four layers:

1. **`local-fast`**
   - should run locally with low friction
   - optimized for early feedback

2. **`ci-required`**
   - must pass on PR and branch CI
   - forms the main merge gate

3. **`release-required`**
   - only needed for release correctness or publishing safety

4. **`observability-only`**
   - trend, reporting, and health signals
   - should not be the first place where a developer learns about a local rule

### Workflow Responsibility Split

#### Shared Android Build Environment

`.github/actions/android-build-env/action.yml` should remain the place for stable shared setup and universally reusable lightweight guards.

It should not become the only place where governance meaning lives.

#### Android CI

`.github/workflows/android-ci.yml` should explicitly own:

- affected-change detection
- compile / lint / unit verification
- `ci-required` quality gates
- PR-facing artifact and diagnostic collection

#### Android Release

`.github/workflows/android-release.yml` should explicitly own:

- release-only verification
- signing checks
- release artifact generation
- publishing logic
- `release-required` gates

### Quality Snapshot Improvements

`scripts/quality/collect_quality_snapshot.sh` should evolve from a flat list of checks into a structured report that includes:

- check name
- tier
- category
- status
- likely remediation path
- source-of-truth file or script

This will make CI failure logs much easier to use.

## Phase C: CI Health Monitor Refinement

### Objective

Keep CI health monitoring valuable without making it the first-alert system for developer mistakes.

### Role Clarification

`.github/workflows/ci-health-monitor.yml` and `scripts/quality/monitor_ci_health.sh` should be treated as trend and governance tools, not primary developer feedback loops.

### Improvements

The health monitor output should distinguish:

- immediate failures that are actively being repaired
- sampled health that is still below threshold because of historical runs
- recovery signals, such as:
  - latest successful run
  - first green after failure streak
  - relevant merged PR or open PR if available

### Issue Lifecycle

The health issue should remain a rolling tracker. Future refinement may add auto-close behavior, but only when:

- sampled metrics recover above threshold
- the issue is clearly no longer representing an active health breach

That auto-close work is optional and later-phase, not phase-one critical.

## Files Expected To Be Touched Over The Initiative

Representative set:

- `.github/actions/android-build-env/action.yml`
- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`
- `.github/workflows/ci-health-monitor.yml`
- `scripts/quality/collect_quality_snapshot.sh`
- `scripts/quality/verify_architecture_boundaries.sh`
- `scripts/quality/verify_ci_workflow_quality.sh`
- `scripts/quality/affected-modules.sh`
- `scripts/quality/monitor_ci_health.sh`
- New: `scripts/quality/preflight_local.sh`
- New: `scripts/quality/check_new_files_guard.sh`
- New: `docs/architecture/ci-quality-gates.md`

Additional helper manifests or tier definitions may be introduced if they reduce duplication cleanly.

## Developer Experience Principles

The new system should optimize for:

1. **One obvious command**
   - a developer should have a single, memorable way to ask “will CI hate this?”

2. **Readable failure output**
   - every important failure should explain what happened and what to do next

3. **Rule discoverability**
   - hidden allowlists and frozen zones must become documented and referenced by tooling

4. **Incremental adoption**
   - changes should not require the team to relearn everything at once

## Validation Criteria

The initiative is successful when all of the following are true:

### Phase A success

- developers can run one local preflight command
- adding a new file in a frozen legacy directory fails locally with a clear explanation
- the developer-facing documentation explains repository-specific quality rules

### Phase B success

- CI workflows clearly separate shared setup, required CI gates, and release-only gates
- quality snapshot output is categorized and easier to interpret
- the same rule is not silently duplicated across layers without a documented reason

### Phase C success

- CI health issue output is more actionable and less ambiguous
- maintainers can distinguish “latest repair succeeded” from “rolling health is restored”

## Risks

### Risk: over-designing the framework

Mitigation:

- preserve existing useful scripts where possible
- reorganize by tier and output clarity rather than rewriting everything

### Risk: local preflight becomes too slow and developers stop using it

Mitigation:

- keep `local-fast` default small
- add opt-in `--full` and `--release` modes instead of forcing everything every time

### Risk: governance docs drift from actual scripts

Mitigation:

- keep the docs derived from real script ownership and names
- where possible, have scripts print their tier/category in a standard format

## Follow-up Direction

After this governance initiative is implemented, the next adjacent improvement would be to revisit GitHub Actions warning hygiene, such as JavaScript action runtime deprecations, using the same tiered governance model so operational warnings do not silently accumulate into future CI churn.
