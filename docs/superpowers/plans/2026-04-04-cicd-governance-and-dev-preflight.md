# CI/CD Governance And Developer Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a phased CI/CD governance system that makes repository-specific quality rules visible locally, reduces CI rule sprawl, and turns CI health monitoring into trend feedback instead of first-discovery.

**Architecture:** Implement this in three delivery phases. Phase A adds a single local preflight command plus a dedicated new-file guard and governance documentation. Phase B reorganizes CI layering and quality snapshot output without weakening existing checks. Phase C refines CI health monitoring so it better reflects repair context and rolling recovery.

**Tech Stack:** GitHub Actions workflows, composite GitHub Action, Bash quality scripts, jq/rg/awk shell tooling, repository docs

---

### Task 1: Baseline The Current CI/CD Governance Surface

**Files:**
- Verify: `.github/actions/android-build-env/action.yml`
- Verify: `.github/workflows/android-ci.yml`
- Verify: `.github/workflows/android-release.yml`
- Verify: `.github/workflows/ci-health-monitor.yml`
- Verify: `scripts/quality/collect_quality_snapshot.sh`
- Verify: `scripts/quality/verify_architecture_boundaries.sh`
- Verify: `scripts/quality/verify_ci_workflow_quality.sh`
- Verify: `scripts/quality/monitor_ci_health.sh`

- [ ] **Step 1: Capture the current workflow and guard inventory**

Run:

```bash
find .github -maxdepth 3 -type f | sort
find scripts/quality -maxdepth 2 -type f | sort
```

Expected:

- You have a complete file inventory for workflows, shared actions, and quality scripts
- You can map existing checks to local, CI, release, or monitoring responsibilities

- [ ] **Step 2: Snapshot the current architecture-boundary experience**

Run:

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
```

Expected:

- The command either passes or fails with the current repository state
- You can inspect its output shape and identify where remediation guidance is weak or missing

- [ ] **Step 3: Record the current quality-snapshot output format**

Run:

```bash
bash scripts/quality/run_quality_gate.sh \
  --project-root . \
  --output-dir /tmp/quality-snapshot-baseline \
  --lint-report app/build/reports/lint-results-debug.txt \
  --source-root app/src/main/kotlin \
  --workflow-file .github/workflows/android-ci.yml
```

Expected:

- The current snapshot output is captured
- You can compare the old flat output with the later tiered output design

### Task 2: Implement Phase A Local Developer Preflight

**Files:**
- Create: `scripts/quality/preflight_local.sh`
- Create: `scripts/quality/check_new_files_guard.sh`
- Modify: `scripts/quality/verify_architecture_boundaries.sh`
- Create: `docs/architecture/ci-quality-gates.md`

- [ ] **Step 1: Add the dedicated new-file guard**

Create `scripts/quality/check_new_files_guard.sh` with logic that:

- detects new Kotlin files under frozen legacy directories such as `app/src/main/kotlin/com/ytone/longcare/features/**`
- reports the exact offending file paths
- points to the governing source-of-truth files:
  - `scripts/quality/legacy_feature_files_allowlist.txt`
  - `scripts/quality/architecture_legacy_imports_allowlist.txt`
- prints suggested fixes such as:
  - inline into an existing allowlisted file
  - move to `feature/*`
  - avoid adding a new file in frozen space

The output should follow a stable structure like:

```text
[new-files-guard][FAIL] Frozen legacy directory
rule_file=scripts/quality/legacy_feature_files_allowlist.txt
offending_file=app/src/main/kotlin/com/ytone/longcare/features/...
recommended_fix=move-to-feature-module-or-inline-into-allowlisted-file
```

- [ ] **Step 2: Add the unified local preflight entrypoint**

Create `scripts/quality/preflight_local.sh` with modes:

- default: `local-fast`
- `--changed-only`
- `--full`
- `--release`

Default behavior should run:

- `check_new_files_guard.sh`
- `verify_architecture_boundaries.sh`
- `verify_module_dependency_whitelist.sh`
- `verify_module_api_visibility.sh`

`--full` should additionally run:

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest`

`--release` should additionally run:

- `bash scripts/quality/run_quality_gate.sh ...`

- [ ] **Step 3: Improve architecture-boundary failure messaging**

Modify `scripts/quality/verify_architecture_boundaries.sh` so the legacy freeze failure includes direct remediation hints instead of only listing file names.

For the legacy file-freeze failure, the output should add lines equivalent to:

```text
[architecture][HINT] app/features is frozen for new Kotlin files
[architecture][HINT] see scripts/quality/legacy_feature_files_allowlist.txt
[architecture][HINT] preferred fix: move code to feature/* or inline into an allowlisted file
```

- [ ] **Step 4: Document the quality-gate system**

Create `docs/architecture/ci-quality-gates.md` with sections for:

- `local-fast`
- `ci-required`
- `release-required`
- `observability-only`

For each existing script, document:

- script path
- purpose
- default execution layer
- common failure modes
- likely remediation path

- [ ] **Step 5: Verify the local preflight experience**

Run:

```bash
bash scripts/quality/preflight_local.sh
bash scripts/quality/preflight_local.sh --full
```

Expected:

- Both commands complete with readable grouped output
- Failures, if any, explain which layer and which script triggered them

- [ ] **Step 6: Commit Phase A**

Run:

```bash
git add scripts/quality/preflight_local.sh \
  scripts/quality/check_new_files_guard.sh \
  scripts/quality/verify_architecture_boundaries.sh \
  docs/architecture/ci-quality-gates.md
git commit -m "feat(ci): add local developer preflight"
```

Expected:

- One commit contains the local-preflight layer and documentation

### Task 3: Implement Phase B CI Quality Layer Reorganization

**Files:**
- Modify: `.github/actions/android-build-env/action.yml`
- Modify: `.github/workflows/android-ci.yml`
- Modify: `.github/workflows/android-release.yml`
- Modify: `scripts/quality/collect_quality_snapshot.sh`
- Modify: `scripts/quality/verify_ci_workflow_quality.sh`

- [ ] **Step 1: Move shared action responsibilities to stable shared guards only**

Refine `.github/actions/android-build-env/action.yml` so it remains responsible only for:

- Java setup
- Gradle setup
- Android SDK setup
- lightweight reusable shell guards that are appropriate for all Android workflows

Avoid encoding workflow-specific business meaning inside the composite action.

- [ ] **Step 2: Make Android CI explicitly own `ci-required` checks**

Adjust `.github/workflows/android-ci.yml` so the workflow structure clearly communicates:

- affected-module detection
- compile/lint/unit verification
- `ci-required` quality gates
- artifact and failure-diagnostics publishing

Prefer explicit step names that include the tier, for example:

```text
Run ci-required quality gates
Collect ci-required quality snapshot
```

- [ ] **Step 3: Make Android Release explicitly own `release-required` checks**

Adjust `.github/workflows/android-release.yml` so release-only responsibilities are visually separated from the base Android CI responsibilities.

Keep:

- signing validation
- release artifact generation
- release publish logic

but label release-only checks distinctly.

- [ ] **Step 4: Add tier and remediation metadata to quality snapshot output**

Modify `scripts/quality/collect_quality_snapshot.sh` so each check entry records:

- `tier`
- `category`
- `likely_fix`
- `source_of_truth`

The markdown report should present something like:

```text
| Check | Tier | Category | Status | Likely Fix | Source |
```

- [ ] **Step 5: Align workflow-quality guard with the new layering**

Update `scripts/quality/verify_ci_workflow_quality.sh` so it validates the new intended layering and step naming without regressing current safety guarantees.

The guard should continue to enforce:

- pinned action versions
- timeout budgets
- artifact retention policies
- shared action usage

while also asserting the presence of the expected tiered structure in `android-ci.yml` and `android-release.yml`.

- [ ] **Step 6: Verify the CI layer reorganization locally**

Run:

```bash
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/run_quality_gate.sh \
  --project-root . \
  --output-dir /tmp/quality-snapshot-tiered \
  --lint-report app/build/reports/lint-results-debug.txt \
  --source-root app/src/main/kotlin \
  --workflow-file .github/workflows/android-ci.yml
```

Expected:

- Workflow quality verification passes
- Quality snapshot output includes clearer tier/category/remediation data

- [ ] **Step 7: Commit Phase B**

Run:

```bash
git add .github/actions/android-build-env/action.yml \
  .github/workflows/android-ci.yml \
  .github/workflows/android-release.yml \
  scripts/quality/collect_quality_snapshot.sh \
  scripts/quality/verify_ci_workflow_quality.sh
git commit -m "refactor(ci): organize quality gates by tier"
```

Expected:

- One commit contains the workflow and snapshot reorganization

### Task 4: Implement Phase C CI Health Monitor Refinement

**Files:**
- Modify: `.github/workflows/ci-health-monitor.yml`
- Modify: `scripts/quality/monitor_ci_health.sh`

- [ ] **Step 1: Enrich monitor output with recovery context**

Modify `scripts/quality/monitor_ci_health.sh` so the generated report can surface:

- latest successful run
- first success after recent failure streak, if available
- whether current rolling health is still below threshold despite a recent fix

If adding PR context is cleanly feasible without overcomplicating the script, include:

- latest merged PR tied to the latest success
- or latest open PR explicitly repairing CI, if detectable

- [ ] **Step 2: Keep issue semantics focused on rolling health**

Adjust `.github/workflows/ci-health-monitor.yml` and the generated report wording so the issue clearly distinguishes:

- latest repair signals
- current rolling-sample threshold status

Do not add auto-close behavior in this phase unless it can be done safely from rolling threshold truth rather than single-run success.

- [ ] **Step 3: Verify monitor output locally**

Run:

```bash
bash scripts/quality/monitor_ci_health.sh \
  "yyg20101/longcare" \
  "50" \
  "scripts/quality/ci_health_thresholds.json" \
  "/tmp/ci-health-local" \
  "" \
  "master"
```

Expected:

- The report is generated
- It includes clearer distinction between current trend status and latest-repair evidence

- [ ] **Step 4: Commit Phase C**

Run:

```bash
git add .github/workflows/ci-health-monitor.yml \
  scripts/quality/monitor_ci_health.sh
git commit -m "improve(ci): refine health monitor recovery context"
```

Expected:

- One commit contains the CI-health-monitor refinement

### Task 5: End-To-End Governance Verification

**Files:**
- Verify: all files touched in Tasks 2-4

- [ ] **Step 1: Run the fast local preflight**

Run:

```bash
bash scripts/quality/preflight_local.sh
```

Expected:

- The command runs successfully and prints tiered, readable output

- [ ] **Step 2: Run the full local preflight**

Run:

```bash
bash scripts/quality/preflight_local.sh --full
```

Expected:

- The full mode completes successfully
- Compile/test failures, if any, are surfaced through the unified preflight flow

- [ ] **Step 3: Run the release-oriented preflight**

Run:

```bash
bash scripts/quality/preflight_local.sh --release
```

Expected:

- Release-oriented checks complete with categorized output

- [ ] **Step 4: Re-run key standalone guards**

Run:

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_module_dependency_whitelist.sh .
```

Expected:

- All three pass

- [ ] **Step 5: Capture final workspace state**

Run:

```bash
git status --short
```

Expected:

- Only intended changes remain
- No stray temporary governance files are left in the repo tree

- [ ] **Step 6: Push the governance branch and run GitHub validation workflows**

Run:

```bash
git push
```

Then trigger and inspect:

```bash
gh workflow run android-ci.yml --repo yyg20101/longcare --ref "$(git branch --show-current)"
gh workflow run ci-health-monitor.yml --repo yyg20101/longcare --ref "$(git branch --show-current)" -f sample_size=50 -f fail_on_breach=false
```

Expected:

- The branch pushes successfully
- Android CI validates the new governance layout
- CI Health Monitor still generates a report after the refinements
