# R65C Release And Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prepare the current `R65C/NFC` work for release by checking the branch boundary against `origin/master`, committing the remaining `docs/superpowers` changes, pushing the current branch, creating a new PR, and merging only after PR checks are green.

**Architecture:** Keep using the current branch, but treat this release as a new delivery unit. Use an explicit boundary-check gate first, then separate the final local commit into a documentation commit, push the branch, open a new PR to `master`, and use GitHub PR checks as the merge gate. Prefer squash merge to avoid carrying reused-branch history into `master`.

**Tech Stack:** Git, GitHub CLI (`gh`), Gradle CI/CD checks, existing branch `codex/release-quality-gate-legacy-freeze-fix`.

---

## Current State Snapshot

- Current branch: `codex/release-quality-gate-legacy-freeze-fix`
- Upstream release target: `origin/master`
- Existing PR for this branch: `#45`, already merged
- Current uncommitted release-scoped work is primarily under `docs/superpowers/**`
- Explicit local change to exclude: `app/src/debug/assets/mock/system_config.json`

## File Structure

### Files to stage and commit in this release plan

- `docs/superpowers/**`
  - All currently modified and untracked documents that the user explicitly chose to include in this release unit

### Files to explicitly exclude from this release plan

- `app/src/debug/assets/mock/system_config.json`
  - Local debug/mock drift, not part of the intended release

### Files to inspect but not modify unless checks fail

- `.github/workflows/**`
- `scripts/**`
- `app/src/main/**`
- `app/src/test/**`
- `app/src/androidTest/**`

These may appear in the branch boundary report because the branch has prior history. Their presence in the boundary report is a release-risk signal, not an instruction to modify them now.

## Release Gate Rule

This plan has one hard stop:

- if the boundary check shows the new PR would include files or features outside the intended release scope, stop and report before pushing or creating the PR

That stop is necessary because the current branch already had a merged PR and now has additional history on top.

### Task 1: Boundary-check the current branch against `origin/master`

**Files:**
- Inspect only: current branch history and working tree

- [ ] **Step 1: Confirm branch name and working tree**

Run:

```bash
git branch --show-current
git status --short
```

Expected:

- branch name is `codex/release-quality-gate-legacy-freeze-fix`
- `docs/superpowers/**` entries are visible in the working tree
- `app/src/debug/assets/mock/system_config.json` is visible and remains unstaged

- [ ] **Step 2: Fetch and sync remote refs**

Run:

```bash
git fetch origin --prune
```

Expected: fetch completes without error and refreshes `origin/master`.

- [ ] **Step 3: Inspect commit divergence**

Run:

```bash
git rev-list --left-right --count origin/master...HEAD
git log --oneline --decorate --left-right origin/master...HEAD | head -n 60
```

Expected:

- output shows that this branch has diverged from `origin/master`
- if the `>` side contains many old commits unrelated to the intended release, note that as a risk

- [ ] **Step 4: Inspect file-level net diff for the prospective PR**

Run:

```bash
git diff --name-status origin/master...HEAD
git diff --stat origin/master...HEAD
```

Expected success condition:

- intended release files dominate the diff
- `nfc`, `nfctest`, `common/utils` reader-related code, tests, `strings.xml`, `docs/superpowers/**`, and any tightly related support files are present

Expected stop condition:

- if the diff includes unrelated feature areas such as dashboard/profile adaptations, legacy CI experiments, or other modules not meant for this release, stop here and report that the branch is too broad for a safe PR

- [ ] **Step 5: Check GitHub PR status**

Run:

```bash
gh pr status
```

Expected:

- old PR `#45` is shown as merged
- there is no currently open PR for the new release unit

### Task 2: Commit the remaining `docs/superpowers` changes and exclude debug drift

**Files:**
- Stage: `docs/superpowers/**`
- Exclude: `app/src/debug/assets/mock/system_config.json`

- [ ] **Step 1: Confirm exactly which release-scoped files remain uncommitted**

Run:

```bash
git status --short docs/superpowers app/src/debug/assets/mock/system_config.json
```

Expected:

- `docs/superpowers/**` shows modified and untracked files
- `system_config.json` is visible and should remain excluded

- [ ] **Step 2: Stage only `docs/superpowers`**

Run:

```bash
git add docs/superpowers
git status --short
```

Expected:

- `docs/superpowers/**` entries move to staged status
- `app/src/debug/assets/mock/system_config.json` remains unstaged

- [ ] **Step 3: Create one documentation release commit**

Run:

```bash
git commit -m "docs: finalize R65C release notes and plans"
```

Expected: commit succeeds and contains only `docs/superpowers/**`.

### Task 3: Push the branch and create a new PR

**Files:**
- Remote branch state only

- [ ] **Step 1: Push the current branch**

Run:

```bash
git push -u origin HEAD
```

Expected:

- branch is pushed successfully
- upstream tracking is confirmed

- [ ] **Step 2: Create a new PR to `master`**

Run:

```bash
gh pr create \
  --base master \
  --head codex/release-quality-gate-legacy-freeze-fix \
  --title "feat(nfc): finalize R65C fallback workflow and docs" \
  --body "## Summary
- finalize R65C fallback workflow behavior
- fix fallback UX and reader state handling
- include supporting docs under docs/superpowers

## Verification
- local focused tests and compile/lint checks passed before PR creation

## Notes
- excludes app/src/debug/assets/mock/system_config.json
- this is a new PR on a previously used branch, so scope was boundary-checked before creation"
```

Expected:

- a new PR URL is returned
- the PR targets `master`

- [ ] **Step 3: Verify the PR scope on GitHub**

Run:

```bash
gh pr view --json number,title,url,baseRefName,headRefName
gh pr diff --stat
```

Expected:

- base is `master`
- head is `codex/release-quality-gate-legacy-freeze-fix`
- diff scope matches the intended release

Stop condition:

- if the PR diff is broader than intended, stop and report before moving to CI checks

### Task 4: Wait for CI/CD to go green before merging

**Files:**
- PR checks only

- [ ] **Step 1: Watch PR checks**

Run:

```bash
gh pr checks --watch
```

Expected:

- checks complete successfully
- if checks fail, capture the failing jobs before making any code change

- [ ] **Step 2: Re-check final PR status**

Run:

```bash
gh pr status
```

Expected:

- the current branch PR is open
- checks are green or approved for merge

- [ ] **Step 3: Merge with squash to keep history clean**

Run:

```bash
gh pr merge --squash --delete-branch=false
```

Expected:

- PR is merged into `master`
- branch is preserved because it has been reused

## Self-Review

### Spec coverage

- continue on current branch: covered by Tasks 1 through 4
- include `nfc` code plus `docs/superpowers`: covered by boundary check and Task 2
- exclude `system_config.json`: covered explicitly in Task 2
- use new PR as CI/CD gate: covered by Tasks 3 and 4
- merge only after checks are green: covered by Task 4

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation notes remain
- Every step contains exact commands and explicit stop/success criteria
- The branch-risk stop condition is explicit, not implied

### Type consistency

- upstream target is consistently `origin/master` and PR base `master`
- branch name is consistently `codex/release-quality-gate-legacy-freeze-fix`
- documentation commit scope is consistently `docs/superpowers/**`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-12-r65c-release-and-merge.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
