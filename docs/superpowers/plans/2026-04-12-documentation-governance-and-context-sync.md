# Documentation Governance And Context Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a durable documentation spine for the project by updating the collaboration entrypoints, creating stable architecture/business/UI/context-sync docs, and moving outdated or low-value material out of the primary path.

**Architecture:** Deliver this in four phases. First create the stable fact documents and session-sync guide, then update `AGENT.md` / `README.md` / `conductor/index.md` to point to the new source of truth, then archive or delete redundant documents, and finally verify that a fresh session can recover context through a short repeatable sequence.

**Tech Stack:** Markdown documentation, repository file organization, Git history preservation through archive moves

---

### Task 1: Build The Stable Fact Documentation Spine

**Files:**
- Create: `docs/architecture/system-overview.md`
- Create: `docs/architecture/business-capability-map.md`
- Create: `docs/architecture/ui-and-screen-map.md`
- Create: `docs/architecture/roadmap-and-open-gaps.md`
- Create: `docs/architecture/session-handoff-guide.md`
- Reference: `conductor/product.md`
- Reference: `conductor/tech-stack.md`
- Reference: `conductor/workflow.md`
- Reference: `conductor/tracks.md`
- Reference: `docs/architecture/dependency-rules.md`
- Reference: `docs/architecture/module-responsibility-map.md`
- Reference: `docs/architecture/ci-quality-gates.md`
- Reference: `app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt`
- Reference: `app/src/main/kotlin/com/ytone/longcare/navigation/NavigationRoutes.kt`

- [ ] **Step 1: Draft `system-overview.md` from the current module and navigation reality**

Create [system-overview.md](/Users/wajie/StudioProjects/longcare/docs/architecture/system-overview.md) covering:

- app shell vs `core/*` vs `feature/*`
- navigation shell and route registration
- data / domain / UI / Android platform boundaries
- external integrations such as AMap, Tencent COS, Bugly, face SDKs

It must describe the current system as it exists, not the ideal future state.

- [ ] **Step 2: Draft `business-capability-map.md` from real product flows**

Create [business-capability-map.md](/Users/wajie/StudioProjects/longcare/docs/architecture/business-capability-map.md) covering:

- login
- service entry and order selection
- identification and face-related flows
- location tracking
- photo upload
- service countdown / completion
- NFC / device-related flows where relevant

For each capability, mark:

- current status
- main route/entry
- key dependencies

- [ ] **Step 3: Draft `ui-and-screen-map.md` using the actual screen inventory**

Create [ui-and-screen-map.md](/Users/wajie/StudioProjects/longcare/docs/architecture/ui-and-screen-map.md) covering:

- screen inventory
- route names / route groups
- which module owns each screen
- key shared UI patterns
- where legacy `app/features` screens still exist

- [ ] **Step 4: Draft `roadmap-and-open-gaps.md`**

Create [roadmap-and-open-gaps.md](/Users/wajie/StudioProjects/longcare/docs/architecture/roadmap-and-open-gaps.md) with sections:

- delivered capabilities
- in-progress modernization work
- known technical debt still accepted
- product/function gaps not yet implemented
- documentation debt and context-sync debt

This file is the durable home for “剩余未开发的功能” and should not depend on reading historical plans.

- [ ] **Step 5: Draft `session-handoff-guide.md`**

Create [session-handoff-guide.md](/Users/wajie/StudioProjects/longcare/docs/architecture/session-handoff-guide.md) with:

- exact fresh-session reading order
- which docs are current truth
- which docs are execution history only
- how to continue active work safely
- what to ignore unless the task requires it

Include a short “5-minute recovery” checklist for a brand new session.

- [ ] **Step 6: Verify the new stable docs are internally consistent**

Run:

```bash
sed -n '1,220p' docs/architecture/system-overview.md
sed -n '1,220p' docs/architecture/business-capability-map.md
sed -n '1,220p' docs/architecture/ui-and-screen-map.md
sed -n '1,220p' docs/architecture/roadmap-and-open-gaps.md
sed -n '1,220p' docs/architecture/session-handoff-guide.md
```

Expected:

- No placeholder text remains
- The docs do not contradict each other on module roles, routes, or current project status

- [ ] **Step 7: Commit the stable-fact docs**

Run:

```bash
git add docs/architecture/system-overview.md \
  docs/architecture/business-capability-map.md \
  docs/architecture/ui-and-screen-map.md \
  docs/architecture/roadmap-and-open-gaps.md \
  docs/architecture/session-handoff-guide.md
git commit -m "docs: add stable architecture and context docs"
```

Expected:

- One commit contains the new stable documentation spine

### Task 2: Update The Entry Documents

**Files:**
- Modify: `AGENT.md`
- Modify: `README.md`
- Modify: `conductor/index.md`
- Reference: `docs/architecture/system-overview.md`
- Reference: `docs/architecture/business-capability-map.md`
- Reference: `docs/architecture/ui-and-screen-map.md`
- Reference: `docs/architecture/roadmap-and-open-gaps.md`
- Reference: `docs/architecture/session-handoff-guide.md`

- [ ] **Step 1: Update `AGENT.md` to become the collaboration entrypoint**

Revise [AGENT.md](/Users/wajie/StudioProjects/longcare/AGENT.md) so it:

- points to the new primary docs
- explains which docs are current truth
- explains where execution history lives
- includes a fresh-session sync checklist

Do not duplicate the full architecture docs inside `AGENT.md`.

- [ ] **Step 2: Update `README.md` to become the external quick-start**

Revise [README.md](/Users/wajie/StudioProjects/longcare/README.md) so it:

- stays lighter than `AGENT.md`
- links to `AGENT.md`, `conductor/index.md`, and the new architecture docs
- removes stale or misleading module descriptions

- [ ] **Step 3: Update `conductor/index.md` as the stable internal map**

Revise [conductor/index.md](/Users/wajie/StudioProjects/longcare/conductor/index.md) so it:

- points to the new stable architecture docs
- clarifies that `docs/superpowers/*` are execution artifacts
- gives the recommended context restoration path for future sessions

- [ ] **Step 4: Verify the entry path works as a fresh-session sequence**

Run:

```bash
sed -n '1,220p' AGENT.md
sed -n '1,220p' README.md
sed -n '1,220p' conductor/index.md
sed -n '1,220p' docs/architecture/session-handoff-guide.md
```

Expected:

- A fresh session can follow a clear path:
  - `AGENT.md`
  - `conductor/index.md`
  - `docs/architecture/session-handoff-guide.md`
- The docs point to the same set of primary truth sources

- [ ] **Step 5: Commit the entrypoint updates**

Run:

```bash
git add AGENT.md README.md conductor/index.md docs/architecture/session-handoff-guide.md
git commit -m "docs: align project entrypoint documents"
```

Expected:

- One commit contains only the entrypoint document updates

### Task 3: Archive Or Remove Redundant Documentation

**Files:**
- Create: `docs/archive/`
- Move candidates from: `docs/refactor/*`
- Move candidates from: `docs/qa/*`
- Move candidates from: `docs/security/*`
- Evaluate selected older architecture plans
- Delete low-value files such as `.DS_Store`

- [ ] **Step 1: Create the archive structure**

Create a simple archive layout, for example:

```text
docs/archive/refactor/
docs/archive/qa/
docs/archive/security/
docs/archive/architecture/
```

- [ ] **Step 2: Move historical refactor, QA, and security documents into archive**

Move documents that are primarily historical rather than current truth.

Initial candidates:

- all of `docs/refactor/*`
- all of `docs/qa/*`
- all of `docs/security/*`

Keep current architecture/rules docs in place if they still define current truth.

- [ ] **Step 3: Evaluate older architecture plan docs**

Review files such as:

- `docs/architecture/project-optimization-refactor-master-plan.md`
- `docs/architecture/ci-cd-automation-optimization-plan.md`
- `docs/architecture/wbcloudface-aar-migration-plan.md`

Move to `docs/archive/architecture/` if they are no longer active source-of-truth documents.

- [ ] **Step 4: Remove low-value clutter**

Delete files such as:

```text
docs/superpowers/.DS_Store
```

Also remove any other clearly low-value duplicate system files discovered during implementation.

- [ ] **Step 5: Verify no primary entrypoint points to moved/deleted docs incorrectly**

Run:

```bash
rg -n "docs/refactor|docs/qa|docs/security|project-optimization-refactor-master-plan|ci-cd-automation-optimization-plan" AGENT.md README.md conductor docs/architecture -S
```

Expected:

- Entry documents either stop referencing archived docs or do so intentionally as historical context only

- [ ] **Step 6: Commit the archive cleanup**

Run:

```bash
git add docs/archive docs/architecture AGENT.md README.md conductor
git commit -m "docs: archive redundant project documentation"
```

Expected:

- One commit contains the archive moves and low-value cleanup

### Task 4: Final Context-Sync Verification

**Files:**
- Verify: all updated docs from Tasks 1-3

- [ ] **Step 1: Perform a simulated fresh-session recovery audit**

Use this reading sequence and verify it is sufficient:

1. `AGENT.md`
2. `conductor/index.md`
3. `docs/architecture/session-handoff-guide.md`
4. `docs/architecture/system-overview.md`
5. `docs/architecture/business-capability-map.md`
6. `docs/architecture/ui-and-screen-map.md`
7. `docs/architecture/roadmap-and-open-gaps.md`
8. `conductor/tracks.md`

Expected:

- The sequence gives enough context to continue collaboration without digging through historical plans first

- [ ] **Step 2: Verify the documentation spine answers the user’s requested dimensions**

Checklist:

```text
- technical architecture documented
- UI/screen design surface documented
- business capabilities documented
- remaining/open functionality documented
- new-session context sync documented
- redundant/historical documents archived or removed
```

Expected:

- All items are satisfied by the final document set

- [ ] **Step 3: Capture final repository status**

Run:

```bash
git status --short
```

Expected:

- Only intended documentation changes remain
- No accidental code changes were introduced

- [ ] **Step 4: Push and prepare for review**

Run:

```bash
git push
```

Expected:

- The documentation-governance branch is updated remotely with all staged documentation changes
