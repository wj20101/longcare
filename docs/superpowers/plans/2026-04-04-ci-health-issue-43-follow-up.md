# CI Health Issue 43 Follow-up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a human status update to GitHub issue `#43` documenting the merged CI repair work while intentionally keeping the issue open until rolling CI health metrics recover above threshold.

**Architecture:** This is a GitHub issue maintenance task, not a code change. The implementation should gather the latest repository facts, post one precise comment to issue `#43`, and verify that the issue remains open after the update.

**Tech Stack:** GitHub issue metadata, GitHub Actions run history, GitHub CLI or GitHub MCP issue-comment tools

---

### Task 1: Confirm The Current Issue And CI State

**Files:**
- Verify: `.github/workflows/ci-health-monitor.yml`
- Verify: `docs/superpowers/specs/2026-04-04-ci-health-issue-43-follow-up-design.md`

- [ ] **Step 1: Confirm issue `#43` is still open and review its latest bot reports**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --comments
```

Expected:

- Issue `#43` is open
- The thread contains CI health report comments posted by `github-actions[bot]`
- No human resolution comment has already superseded this follow-up

- [ ] **Step 2: Confirm the repair evidence that must be referenced**

Run:

```bash
gh pr view 44 --repo yyg20101/longcare
gh run view 23969631822 --repo yyg20101/longcare
gh run view 23970826638 --repo yyg20101/longcare
```

Expected:

- PR `#44` is merged
- `Android Release` run `23969631822` succeeded
- `master` `Android CI` run `23970826638` succeeded

- [ ] **Step 3: Confirm the issue should remain open**

Run:

```bash
sed -n '1,220p' .github/workflows/ci-health-monitor.yml
```

Expected:

- The workflow creates or updates the issue on breach
- The workflow does not automatically close the issue on recovery
- Keeping the issue open until the sampled health window recovers remains consistent with repository policy

### Task 2: Post The Manual Status Comment To Issue 43

**Files:**
- Verify: `docs/superpowers/specs/2026-04-04-ci-health-issue-43-follow-up-design.md`

- [ ] **Step 1: Prepare the exact comment body**

Use this exact comment body unless a tiny wording adjustment is needed for clarity:

```markdown
PR #44 has been merged and the release-quality-gate failure path has been repaired.

Validation completed with:
- Android Release run `23969631822` succeeded
- latest `master` Android CI run `23970826638` succeeded

Keeping this issue open for now because `#43` tracks rolling CI health over the monitor sample window, not just the latest successful run. Once the sampled metrics recover above threshold, this issue can be closed.
```

- [ ] **Step 2: Post the comment to issue `#43`**

Run one of the following, depending on the available tool path:

GitHub CLI option:

```bash
gh issue comment 43 --repo yyg20101/longcare --body "PR #44 has been merged and the release-quality-gate failure path has been repaired.

Validation completed with:
- Android Release run \`23969631822\` succeeded
- latest \`master\` Android CI run \`23970826638\` succeeded

Keeping this issue open for now because \`#43\` tracks rolling CI health over the monitor sample window, not just the latest successful run. Once the sampled metrics recover above threshold, this issue can be closed."
```

Expected:

- A new human-authored comment is added to issue `#43`
- The comment references PR `#44` and the two successful runs

- [ ] **Step 3: Do not change issue state**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --json state
```

Expected:

- The returned state is still `OPEN`

### Task 3: Verify The Follow-up Is Accurate And Complete

**Files:**
- Verify: `docs/superpowers/specs/2026-04-04-ci-health-issue-43-follow-up-design.md`

- [ ] **Step 1: Re-read the issue thread to confirm the new comment landed correctly**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --comments
```

Expected:

- The newest human comment contains the intended text
- The issue remains open
- No accidental duplicate comments were posted

- [ ] **Step 2: Verify the comment does not overclaim recovery**

Check the posted comment against this checklist:

```text
- states PR #44 is merged
- states Android Release run 23969631822 succeeded
- states master Android CI run 23970826638 succeeded
- states issue remains open because rolling CI health has not yet been re-evaluated above threshold
- does not claim that CI health is fully restored
```

Expected:

- Every line above is satisfied

- [ ] **Step 3: Capture the final local workspace state**

Run:

```bash
git status --short
```

Expected:

- No repository source-code changes were required for this issue follow-up
- Existing untracked planning documents may remain in the working tree without affecting the issue update
