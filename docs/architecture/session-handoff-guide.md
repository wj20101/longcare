# Session Handoff Guide

Last verified: 2026-04-12

This guide defines how a new session should recover deeper project context after starting from `AGENT.md`.

## 1) Fresh-session reading order (exact)

Use this order when `AGENT.md` alone is not enough:

1. `AGENT.md`
2. `conductor/index.md`
3. `docs/architecture/session-handoff-guide.md`
4. `docs/architecture/system-overview.md`
5. `docs/architecture/business-capability-map.md`
6. `docs/architecture/ui-and-screen-map.md`
7. `docs/architecture/roadmap-and-open-gaps.md`
8. `conductor/product.md`
9. `conductor/tech-stack.md`
10. `conductor/workflow.md`

Then read only the task-relevant code paths.

## 2) Docs that are current truth

Primary truth set:

- `docs/architecture/system-overview.md`
- `docs/architecture/business-capability-map.md`
- `docs/architecture/ui-and-screen-map.md`
- `docs/architecture/roadmap-and-open-gaps.md`
- `docs/architecture/dependency-rules.md`
- `docs/architecture/module-responsibility-map.md`
- `docs/architecture/ci-quality-gates.md`
- `conductor/product.md`
- `conductor/tech-stack.md`
- `conductor/workflow.md`

Useful progress/context docs (not stable truth):

- `conductor/tracks.md` (workstream status and momentum tracking)

## 3) Docs that are execution history (not primary truth)

Use these as historical context only when needed:

- long-form optimization/refactor plan documents
- phase-by-phase execution artifacts under `docs/superpowers/`
- historical reports/checklists that describe prior milestones

If history conflicts with code and stable architecture docs, trust current code + stable architecture docs.

## 4) How to continue active work safely

- confirm allowed edit scope before touching files
- verify current route/module ownership from:
  - `docs/architecture/ui-and-screen-map.md`
  - `app/src/main/kotlin/com/ytone/longcare/navigation/*`
- verify capability assumptions from:
  - `docs/architecture/business-capability-map.md`
- verify boundaries before structural edits from:
  - `docs/architecture/dependency-rules.md`
  - `docs/architecture/module-responsibility-map.md`
- run minimum relevant checks before claiming completion

## 5) What to ignore unless task requires it

- broad historical plan narratives not tied to current task
- archived or obsolete checklists when task only needs current runtime facts
- non-related feature code trees outside the routes/modules being changed

## 6) 5-minute recovery checklist

Use this checklist after reading `AGENT.md` and deciding you need more than the default single-entry summary:

1. Read `AGENT.md` and `conductor/index.md` (entry context).
2. Read the three architecture fact maps:
   - system overview
   - business capability map
   - UI/screen map
3. Confirm the task touches which route group:
   - entry
   - service flow
   - support
4. Confirm ownership reality:
   - `:app` vs `:feature:*` for the touched screens
5. Confirm guardrails:
   - dependency/module rules
   - CI quality gate expectations
6. Start implementation with smallest verified change slice.

## 7) Quick command aids for recovery

Use these to re-check current truth quickly:

```bash
sed -n '1,220p' docs/architecture/system-overview.md
sed -n '1,220p' docs/architecture/business-capability-map.md
sed -n '1,220p' docs/architecture/ui-and-screen-map.md
sed -n '1,220p' docs/architecture/roadmap-and-open-gaps.md
rg -n "composable<|navigateTo" app/src/main/kotlin/com/ytone/longcare/navigation --glob '*.kt'
```
