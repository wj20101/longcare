# Lint Waiver Drift Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove stale lint waiver entries that no longer match the current lint report and document the intended local-vs-CI enforcement split so local release-oriented quality checks can pass again.

**Architecture:** Keep the change intentionally narrow. Update the waiver manifest to remove only the two stale entries, preserve the existing enforcement behavior in `verify_lint_warning_allowlist.sh`, and add a short explanatory note to the CI quality gate documentation.

**Tech Stack:** Bash lint guard scripts, JSON waiver manifest, repository governance documentation

---

### Task 1: Reconfirm The Current Waiver Drift

**Files:**
- Verify: `scripts/lint/lint_warning_waivers.json`
- Verify: `scripts/lint/verify_lint_warning_allowlist.sh`
- Verify: `app/build/reports/lint-results-debug.txt`

- [ ] **Step 1: Re-run the lint waiver allowlist check to capture the current failure**

Run:

```bash
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

Expected:

- Exit code `1`
- Output reports stale waivers:

```text
GradleDependency
NewerVersionAvailable
```

- [ ] **Step 2: Confirm the current lint report only contains the active warning IDs**

Run:

```bash
sed -n '1,200p' app/build/reports/lint-results-debug.txt
```

Expected:

- The report contains warning IDs still in active use:
  - `Aligned16KB`
  - `GlobalOptionInConsumerRules`
  - `TrustAllX509TrustManager`
- The report does not contain:
  - `GradleDependency`
  - `NewerVersionAvailable`

- [ ] **Step 3: Confirm the current script behavior should remain unchanged**

Run:

```bash
sed -n '1,260p' scripts/lint/verify_lint_warning_allowlist.sh
```

Expected:

- In local/default mode, stale waivers remain blocking
- In CI `auto` mode, stale waivers are advisory unless `LINT_ENFORCE_UNUSED_WAIVERS=true`
- No script logic change is required for this cleanup

### Task 2: Remove Only The Stale Waiver Entries

**Files:**
- Modify: `scripts/lint/lint_warning_waivers.json`

- [ ] **Step 1: Delete the two stale waiver objects from the manifest**

Update [lint_warning_waivers.json](/Users/wajie/StudioProjects/longcare/scripts/lint/lint_warning_waivers.json) so these two entries are removed:

```json
{
  "id": "GradleDependency",
  "owner": "mobile-platform",
  "review_by": "2026-06-30",
  "reason": "Version catalog intentionally pins SDK and plugin versions for release-train stability. Re-evaluate on dependency upgrade cadence.",
  "allowed_sources": [
    "gradle/libs.versions.toml"
  ]
},
{
  "id": "NewerVersionAvailable",
  "owner": "mobile-platform",
  "review_by": "2026-06-30",
  "reason": "Version catalog intentionally lags latest artifacts to keep compatibility baseline stable. Revisit during scheduled dependency refresh.",
  "allowed_sources": [
    "gradle/libs.versions.toml"
  ]
}
```

After the edit, the remaining waiver IDs should be exactly:

```json
[
  "Aligned16KB",
  "GlobalOptionInConsumerRules",
  "TrustAllX509TrustManager"
]
```

- [ ] **Step 2: Validate the JSON manifest after the edit**

Run:

```bash
jq '.waivers[].id' scripts/lint/lint_warning_waivers.json
```

Expected:

- Output shows only:
  - `Aligned16KB`
  - `GlobalOptionInConsumerRules`
  - `TrustAllX509TrustManager`

- [ ] **Step 3: Re-run the lint waiver allowlist check**

Run:

```bash
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

Expected:

- Exit code `0`
- Output ends with:

```text
Lint warning waiver check passed.
```

### Task 3: Document The Local vs CI Enforcement Split

**Files:**
- Modify: `docs/architecture/ci-quality-gates.md`

- [ ] **Step 1: Add a short note describing stale-waiver enforcement behavior**

Update [ci-quality-gates.md](/Users/wajie/StudioProjects/longcare/docs/architecture/ci-quality-gates.md) in the lint-related section or local preflight assumptions with text equivalent to:

```md
### Lint waiver stale-entry behavior

- Local/default execution treats stale waiver entries as blocking so waiver drift is cleaned up early.
- CI auto mode treats stale waiver entries as non-blocking to avoid noisy red pipelines caused by timing drift between validation windows.
- CI can be made strict by setting `LINT_ENFORCE_UNUSED_WAIVERS=true`.
```

Keep the explanation short and policy-focused. Do not expand this into a full lint-governance redesign.

- [ ] **Step 2: Sanity-check the updated documentation**

Run:

```bash
sed -n '1,260p' docs/architecture/ci-quality-gates.md
```

Expected:

- The new note is present
- It matches the actual behavior in `verify_lint_warning_allowlist.sh`

### Task 4: Verify Release-Oriented Local Quality Flow

**Files:**
- Verify: `scripts/lint/lint_warning_waivers.json`
- Verify: `docs/architecture/ci-quality-gates.md`
- Verify: `scripts/quality/preflight_local.sh`
- Verify: `scripts/quality/run_quality_gate.sh`

- [ ] **Step 1: Re-run the unified quality gate**

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

- `Lint Warning Allowlist` no longer fails
- If the command still fails, it should fail for a different reason than stale `GradleDependency` / `NewerVersionAvailable` waivers

- [ ] **Step 2: Re-run release-oriented local preflight**

Run:

```bash
bash scripts/quality/preflight_local.sh --release
```

Expected:

- The flow progresses beyond the previous lint-waiver-drift blocker
- If the command still fails, the failure is not stale waiver drift

- [ ] **Step 3: Capture the final diff**

Run:

```bash
git diff -- scripts/lint/lint_warning_waivers.json docs/architecture/ci-quality-gates.md
```

Expected:

- Only the two stale waiver entries are removed
- Documentation change is limited to the local-vs-CI stale-waiver explanation

- [ ] **Step 4: Commit the cleanup**

Run:

```bash
git add scripts/lint/lint_warning_waivers.json docs/architecture/ci-quality-gates.md
git commit -m "chore(lint): remove stale warning waivers"
```

Expected:

- One commit contains the waiver cleanup and small documentation update
