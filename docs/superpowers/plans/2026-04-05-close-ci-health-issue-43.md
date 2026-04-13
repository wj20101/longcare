# Close CI Health Issue 43 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close GitHub issue `#43` after confirming the latest rolling CI health report on `master` is healthy and leaving a short factual closing note.

**Architecture:** This is a GitHub issue maintenance task, not a code change. The work is limited to reconfirming the latest successful CI runs and health-monitor report, posting a short closing comment, closing the issue, and verifying the final issue state.

**Tech Stack:** GitHub issue metadata, GitHub Actions run history, GitHub CLI or GitHub MCP issue tools

---

### Task 1: Reconfirm The Recovery State

**Files:**
- Verify: `docs/superpowers/specs/2026-04-05-close-ci-health-issue-43-design.md`
- Verify: `.github/workflows/ci-health-monitor.yml`

- [ ] **Step 1: Reconfirm the latest successful `master` signals**

Run:

```bash
gh run view 23990163815 --repo yyg20101/longcare
gh run view 23971093894 --repo yyg20101/longcare
gh run view 23990269403 --repo yyg20101/longcare --log
```

Expected:

- `Android CI` run `23990163815` is successful
- `Android Release` run `23971093894` is successful
- `CI Health Monitor` run `23990269403` is successful and its report states `Rolling threshold status: PASS`

- [ ] **Step 2: Confirm issue `#43` is still open before closure**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --json state
```

Expected:

- The returned state is `OPEN`

### Task 2: Add A Closing Comment And Close The Issue

**Files:**
- Verify: `docs/superpowers/specs/2026-04-05-close-ci-health-issue-43-design.md`

- [ ] **Step 1: Prepare the exact closing note**

Use this comment body:

```markdown
Latest `master` signals are now healthy:

- Android CI run `23990163815` succeeded
- Android Release run `23971093894` succeeded
- CI Health Monitor run `23990269403` succeeded and reported `Rolling threshold status: PASS`

Closing this issue because the rolling CI health breach tracked by `#43` has recovered.
```

- [ ] **Step 2: Post the closing comment**

Run:

```bash
gh issue comment 43 --repo yyg20101/longcare --body "Latest \`master\` signals are now healthy:

- Android CI run \`23990163815\` succeeded
- Android Release run \`23971093894\` succeeded
- CI Health Monitor run \`23990269403\` succeeded and reported \`Rolling threshold status: PASS\`

Closing this issue because the rolling CI health breach tracked by \`#43\` has recovered."
```

Expected:

- A new human-authored closing comment is added to issue `#43`

- [ ] **Step 3: Close the issue**

Run:

```bash
gh issue close 43 --repo yyg20101/longcare
```

Expected:

- Issue `#43` transitions to `CLOSED`

### Task 3: Verify Final Issue State

**Files:**
- Verify: `docs/superpowers/specs/2026-04-05-close-ci-health-issue-43-design.md`

- [ ] **Step 1: Re-read the issue after closure**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --comments
```

Expected:

- The new closing comment is present
- The issue is shown as closed

- [ ] **Step 2: Confirm the state programmatically**

Run:

```bash
gh issue view 43 --repo yyg20101/longcare --json state
```

Expected:

- The returned state is `CLOSED`

- [ ] **Step 3: Capture final local workspace state**

Run:

```bash
git status --short
```

Expected:

- No repository source-code changes were required for this task
- Existing untracked planning documents may remain in the working tree without affecting issue closure
