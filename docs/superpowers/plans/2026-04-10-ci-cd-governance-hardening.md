# CI/CD Governance Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce repeat CI/CD failures by formalizing gate metadata, standardizing diagnostics, and tightening Android Release preconditions without replacing the current pipeline.

**Architecture:** Introduce a small machine-readable gate registry as the single descriptive source for gate ownership and layering. Keep existing shell scripts and workflows as runners, but teach them to emit consistent diagnostics and to consume the registry where practical. Add a narrow Android Release precondition check so release confirms readiness instead of rediscovering ordinary CI drift.

**Tech Stack:** Bash, JSON, GitHub Actions YAML, jq, Gradle Android workflows

---

## File Structure

- Create: `scripts/quality/quality_gate_registry.json`
  - Machine-readable registry for high-value gates.
- Modify: `scripts/quality/preflight_local.sh`
  - Improve layer-aware summaries for local execution.
- Modify: `scripts/quality/collect_quality_snapshot.sh`
  - Standardize emitted diagnostics using registry metadata.
- Modify: `scripts/lint/verify_lint_warning_allowlist.sh`
  - Emit richer failure context and registry-aligned remediation hints.
- Modify: `.github/workflows/android-ci.yml`
  - Publish clearer gate-layer summaries.
- Modify: `.github/workflows/android-release.yml`
  - Add explicit precondition guard for prior `Android CI` success on the release target commit.
- Modify: `docs/architecture/ci-quality-gates.md`
  - Document the registry-backed layered governance model.
- Optional modify: `conductor/workflow.md`
  - Keep project workflow docs aligned.

## Task 1: Add the gate registry and document its contract

**Files:**
- Create: `scripts/quality/quality_gate_registry.json`
- Modify: `docs/architecture/ci-quality-gates.md`

- [ ] **Step 1: Create the initial gate registry with current high-value gates**

Create `scripts/quality/quality_gate_registry.json` with this initial content:

```json
{
  "version": 1,
  "gates": [
    {
      "id": "no_tracked_keystore_files",
      "name": "No Tracked Keystore Files",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/verify_no_tracked_keystore_files.sh",
      "likely_fix": "Remove tracked keystore artifacts and rotate secrets."
    },
    {
      "id": "lint_warning_allowlist",
      "name": "Lint Warning Allowlist",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/lint/lint_warning_waivers.json",
      "likely_fix": "Fix the lint issue or add/update an approved waiver entry."
    },
    {
      "id": "lint_ignore_policy",
      "name": "Lint Ignore Policy Guard",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "app/lint.xml",
      "likely_fix": "Remove the forbidden lint ignore and fix the root warning."
    },
    {
      "id": "architecture_boundaries",
      "name": "Architecture Boundaries",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/verify_architecture_boundaries.sh",
      "likely_fix": "Move code to the allowed layer or update the explicit architecture allowlist."
    },
    {
      "id": "module_dependency_whitelist",
      "name": "Module Dependency Whitelist",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/module_dependency_allowlist.txt",
      "likely_fix": "Restore the approved module dependency edge or update the allowlist deliberately."
    },
    {
      "id": "module_api_visibility",
      "name": "Module API Visibility",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/verify_module_api_visibility.sh",
      "likely_fix": "Move public contracts back to approved API boundaries."
    },
    {
      "id": "workflow_quality",
      "name": "CI Workflow Quality Guard",
      "layer": "ci-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/verify_ci_workflow_quality.sh",
      "likely_fix": "Restore workflow governance requirements such as retention, pinning, or trigger policy."
    },
    {
      "id": "release_exported_components",
      "name": "Release Exported Component Allowlist",
      "layer": "release-required",
      "blocking": true,
      "owner": "mobile-platform",
      "source_of_truth": "scripts/quality/verify_release_exported_components.sh",
      "likely_fix": "Align exported manifest components with the release allowlist."
    }
  ]
}
```

- [ ] **Step 2: Validate the JSON shape**

Run:

```bash
jq . scripts/quality/quality_gate_registry.json >/dev/null
```

Expected:

- exit code `0`

- [ ] **Step 3: Update the CI quality-gates doc to mention the registry**

Add a short section to `docs/architecture/ci-quality-gates.md` after the tier definitions:

```md
## Gate Registry

The canonical descriptive metadata for high-value gates lives in:

- `scripts/quality/quality_gate_registry.json`

The registry does not replace the current runner scripts. Instead, it defines:

- gate ownership
- gate layer
- source of truth
- likely remediation path

Runner scripts and workflows should emit diagnostics that align with this registry.
```

- [ ] **Step 4: Commit the registry and doc contract**

```bash
git add scripts/quality/quality_gate_registry.json docs/architecture/ci-quality-gates.md
git commit -m "docs(ci): add quality gate registry contract"
```

## Task 2: Standardize quality snapshot diagnostics with registry metadata

**Files:**
- Modify: `scripts/quality/collect_quality_snapshot.sh`
- Modify: `scripts/lint/verify_lint_warning_allowlist.sh`
- Test: `scripts/quality/quality_gate_registry.json`

- [ ] **Step 1: Add a small helper in `collect_quality_snapshot.sh` to read registry metadata**

Insert this helper before the main check loop:

```bash
REGISTRY_PATH="${PROJECT_ROOT}/scripts/quality/quality_gate_registry.json"

registry_lookup() {
  local gate_name="$1"
  local field="$2"
  jq -r --arg gate_name "${gate_name}" --arg field "${field}" '
    .gates[]
    | select(.name == $gate_name)
    | .[$field]
  ' "${REGISTRY_PATH}" 2>/dev/null | head -n1
}
```

- [ ] **Step 2: Enrich the markdown snapshot with owner/source/remediation**

Extend the markdown rendering block so each failed check includes:

```bash
REGISTRY_OWNER="$(registry_lookup "${NAME}" owner)"
REGISTRY_SOURCE="$(registry_lookup "${NAME}" source_of_truth)"
REGISTRY_FIX="$(registry_lookup "${NAME}" likely_fix)"
REGISTRY_LAYER="$(registry_lookup "${NAME}" layer)"
```

and render in markdown:

```md
- owner: `...`
- layer: `...`
- source of truth: `...`
- likely fix: `...`
```

- [ ] **Step 3: Improve the lint allowlist failure output**

In `scripts/lint/verify_lint_warning_allowlist.sh`, replace the current unknown-ID failure block with:

```bash
if [[ -n "${UNKNOWN_IDS}" ]]; then
  echo "Found lint warning IDs outside waiver allowlist:" >&2
  printf '%s' "${UNKNOWN_IDS}" | sed 's/^/  - /' >&2
  echo "Owner: mobile-platform" >&2
  echo "Source of truth: scripts/lint/lint_warning_waivers.json" >&2
  echo "Likely fix: either fix the warning at source or add a constrained waiver entry with owner/review_by/allowed_sources." >&2
  echo "Observed warning IDs in report:" >&2
  printf '%s\n' "${WARNING_IDS}" | sed 's/^/  - /' >&2
  exit 1
fi
```

- [ ] **Step 4: Re-run the lint allowlist check**

Run:

```bash
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

Expected:

- PASS when report is aligned with waivers
- or, on failure, enriched diagnostics are printed

- [ ] **Step 5: Rebuild a quality snapshot locally**

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

- snapshot emits clearer failure/ownership metadata

- [ ] **Step 6: Commit the diagnostics hardening**

```bash
git add scripts/quality/collect_quality_snapshot.sh scripts/lint/verify_lint_warning_allowlist.sh
git commit -m "fix(ci): enrich gate failure diagnostics"
```

## Task 3: Make local preflight output layer-aware

**Files:**
- Modify: `scripts/quality/preflight_local.sh`
- Test: `scripts/quality/quality_gate_registry.json`

- [ ] **Step 1: Add a short layer summary banner at the top of local preflight**

Insert after mode parsing:

```bash
echo "[preflight] mode=${MODE}"
echo "[preflight] layer mapping:"
echo "  - local-fast: developer prevention checks"
echo "  - ci-required: merge-blocking CI checks"
echo "  - release-required: release confidence checks"
echo
```

- [ ] **Step 2: Add an explicit transition notice before release mode invokes the snapshot gate**

Before running `run_quality_gate.sh` in `--release` mode, print:

```bash
echo "[preflight] entering release-required gate layer via run_quality_gate.sh"
echo
```

- [ ] **Step 3: Re-run local preflight in fast and release modes**

Run:

```bash
bash scripts/quality/preflight_local.sh --local-fast
bash scripts/quality/preflight_local.sh --release
```

Expected:

- Output clearly distinguishes local-fast from release-required phases

- [ ] **Step 4: Commit the preflight output improvements**

```bash
git add scripts/quality/preflight_local.sh
git commit -m "fix(ci): clarify preflight gate layers"
```

## Task 4: Tighten Android CI and Android Release workflow semantics

**Files:**
- Modify: `.github/workflows/android-ci.yml`
- Modify: `.github/workflows/android-release.yml`
- Modify: `.github/actions/android-build-env/action.yml`

- [ ] **Step 1: Add a gate summary step to `Android CI`**

In `.github/workflows/android-ci.yml`, add a summary step before `Run ci-required quality gates`:

```yaml
      - name: Publish ci-required gate summary
        run: |
          {
            echo "## CI required gates"
            echo ""
            echo "- lint warning allowlist"
            echo "- lint ignore policy"
            echo "- jetpack compat guard"
            echo "- baseline profile journeys"
            echo "- module dependency whitelist"
            echo "- architecture boundaries"
            echo "- module API visibility"
            echo "- workflow quality guard"
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 2: Add a release precondition guard to `Android Release`**

In `.github/workflows/android-release.yml`, add a step after checkout:

```yaml
      - name: Verify Android CI success for target commit
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          set -euo pipefail
          TARGET_SHA="${GITHUB_SHA}"
          RUN_JSON="$(gh run list \
            --workflow \"Android CI\" \
            --branch \"${GITHUB_REF_NAME}\" \
            --commit \"${TARGET_SHA}\" \
            --limit 1 \
            --json status,conclusion,headSha)"

          MATCH_COUNT="$(printf '%s' "${RUN_JSON}" | jq 'length')"
          if [ "${MATCH_COUNT}" -eq 0 ]; then
            echo "::error::No Android CI run found for commit ${TARGET_SHA}. Run Android CI first."
            exit 1
          fi

          STATUS="$(printf '%s' "${RUN_JSON}" | jq -r '.[0].status')"
          CONCLUSION="$(printf '%s' "${RUN_JSON}" | jq -r '.[0].conclusion')"

          if [ "${STATUS}" != "completed" ] || [ "${CONCLUSION}" != "success" ]; then
            echo "::error::Android CI for ${TARGET_SHA} is not green (status=${STATUS}, conclusion=${CONCLUSION})."
            exit 1
          fi
```

- [ ] **Step 3: Clarify the shared environment action role**

In `.github/actions/android-build-env/action.yml`, update the description line to something like:

```yaml
description: Configure Java/Gradle/Android SDK and run shared low-cost Android guardrails. This action is environment/setup-focused and should not replace workflow-specific gate selection.
```

- [ ] **Step 4: Validate workflow syntax**

Run:

```bash
bash scripts/quality/verify_ci_workflow_quality.sh
```

Expected:

- PASS

- [ ] **Step 5: Commit the workflow semantics hardening**

```bash
git add .github/workflows/android-ci.yml .github/workflows/android-release.yml .github/actions/android-build-env/action.yml
git commit -m "fix(ci): harden workflow gate semantics"
```

## Self-Review

### Spec coverage

- Gate registry: Task 1
- Diagnostics standardization: Task 2
- Local preflight layer clarity: Task 3
- Release precondition tightening: Task 4
- Incremental rollout: tasks are staged and separable

### Placeholder scan

- No `TODO` / `TBD`
- All tasks contain exact file paths, code, and commands
- Validation steps are concrete

### Type consistency

- `quality_gate_registry.json`
- `registry_lookup`
- `Verify Android CI success for target commit`
- `run_quality_gate.sh`
- `collect_quality_snapshot.sh`

Names are consistent across tasks.
