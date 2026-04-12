# Documentation Governance And Context Sync Design

## Background

The repository already contains multiple documentation sources with overlapping responsibilities:

- `AGENT.md`
- `README.md`
- `conductor/*.md`
- `docs/architecture/*`
- `docs/refactor/*`
- `docs/qa/*`
- `docs/security/*`
- `docs/superpowers/specs/*`
- `docs/superpowers/plans/*`

This has helped preserve important history, but it also creates real coordination cost:

- the “current truth” is spread across too many files
- some documents are durable and should keep evolving
- some are historical execution artifacts and should not be treated as current truth
- some are redundant or low-value and should be archived or deleted

The user also wants a reliable way to start a fresh session and quickly restore the right project context.

## Goal

Create a durable documentation system that:

- updates `AGENT.md` into a better collaboration entrypoint
- records the project’s technical architecture, UI surface, business capabilities, and open gaps in stable documents
- archives or removes redundant documents
- defines a clear “how to sync context in a new session” workflow
- keeps planning/spec artifacts available without confusing them with the current source of truth

## Non-Goals

- Do not rewrite every historical document into a new format.
- Do not delete execution history that still has audit or project-tracking value.
- Do not move all `docs/superpowers/*` execution artifacts into the main architecture docs.
- Do not make this initiative depend on code refactors.

## Options Considered

### Option 1: Structured governance with primary docs + archive, recommended

Keep a small set of stable primary documents, preserve execution history separately, and archive historical or transitional material.

Pros:

- Best balance between clarity and traceability
- Supports both humans and future agent sessions
- Reduces maintenance cost without losing important history

Cons:

- Requires careful curation and classification work

### Option 2: Add new docs only, keep everything else in place

Pros:

- Fastest to ship

Cons:

- Creates yet another documentation layer
- Does not reduce context drift or duplication

### Option 3: Aggressive document deletion

Pros:

- Produces a very clean repository surface quickly

Cons:

- Risks deleting still-useful migration and execution context
- Makes historical reconstruction harder

## Decision

Use Option 1.

The repository needs a stable documentation spine, not another parallel layer and not a destructive cleanup. A structured system with primary documents, execution artifacts, and archive zones best supports long-term collaboration.

## Documentation Model

The repository documentation should be organized into five layers.

### 1. Entry Layer

These are the first documents a person or coding agent should read when entering the repository.

Keep and strengthen:

- `AGENT.md`
- `README.md`
- `conductor/index.md`

Responsibilities:

- quick project orientation
- where current truth lives
- how to start work
- which documents to read next

### 2. Stable Fact Layer

These are the long-lived, continuously maintained documents representing the current project truth.

Keep or create:

- `conductor/product.md`
- `conductor/tech-stack.md`
- `conductor/workflow.md`
- `conductor/tracks.md`
- `docs/architecture/system-overview.md`
- `docs/architecture/business-capability-map.md`
- `docs/architecture/ui-and-screen-map.md`
- `docs/architecture/roadmap-and-open-gaps.md`

Responsibilities:

- product scope and core flows
- technical architecture and module model
- screen inventory and UI structure
- currently implemented features vs remaining work

### 3. Rules Layer

These documents define durable constraints and collaboration rules.

Keep or refine:

- `docs/architecture/dependency-rules.md`
- `docs/architecture/module-responsibility-map.md`
- `docs/architecture/ci-quality-gates.md`
- new: `docs/architecture/session-handoff-guide.md`

Responsibilities:

- architecture boundaries
- module ownership
- CI/CD and quality gates
- how to restore context in a fresh session

### 4. Execution Layer

These documents record per-task design and implementation history.

Keep as execution artifacts:

- `docs/superpowers/specs/*`
- `docs/superpowers/plans/*`

Responsibilities:

- task-specific design history
- execution plans
- implementation traceability

Important rule:

- These documents are not the canonical source of current project truth.

### 5. Archive Layer

These documents contain useful history but should no longer compete with primary docs.

Create and use:

- `docs/archive/`

Move likely candidates from:

- `docs/refactor/*`
- `docs/qa/*`
- `docs/security/*`
- one-off architecture migration plans that are no longer active

Responsibilities:

- preserve historical context
- reduce current-document noise

## Primary New Documents

### `docs/architecture/system-overview.md`

Must cover:

- system/module architecture
- app shell vs feature/core responsibilities
- navigation structure
- high-level state and data flow
- external integrations and platform dependencies

### `docs/architecture/business-capability-map.md`

Must cover:

- business domains
- implemented capabilities
- key user flows
- major operational constraints

### `docs/architecture/ui-and-screen-map.md`

Must cover:

- screen inventory
- route/entry relationships
- screen ownership by module
- important UI patterns and page clusters

### `docs/architecture/roadmap-and-open-gaps.md`

Must cover:

- delivered features
- unfinished features
- technical debt still intentionally accepted
- near-term backlog themes

### `docs/architecture/session-handoff-guide.md`

Must answer:

- if a new session starts, what should be read first
- which files are current truth
- how to choose between `conductor`, `docs/architecture`, and `docs/superpowers`
- how to continue active work safely

## Session Sync Design

The fresh-session context restoration path should be explicit and simple:

1. Read `AGENT.md`
2. Read `conductor/index.md`
3. Read `docs/architecture/session-handoff-guide.md`
4. Then read, as needed:
   - `docs/architecture/system-overview.md`
   - `docs/architecture/business-capability-map.md`
   - `docs/architecture/ui-and-screen-map.md`
   - `docs/architecture/roadmap-and-open-gaps.md`
   - `conductor/tracks.md`

This sequence should be documented in both:

- `AGENT.md`
- `docs/architecture/session-handoff-guide.md`

## Archive / Cleanup Strategy

### Keep In Place

Keep these as living documents:

- `AGENT.md`
- `README.md`
- `conductor/*.md`
- selected `docs/architecture/*.md`
- `docs/superpowers/specs/*`
- `docs/superpowers/plans/*`

### Archive

Archive documents that are primarily:

- historical refactor reports
- one-time migration plans
- outdated QA verification reports
- security incident or credential-rotation records that should be preserved but are not active project truth

Examples of likely archive candidates:

- `docs/refactor/*`
- `docs/qa/*`
- `docs/security/*`
- older architecture optimization plans no longer driving current work

### Delete

Delete only clearly low-value artifacts such as:

- `.DS_Store`
- duplicate generated clutter
- temporary files with no remaining reference value

Deletion should be conservative and only after verifying the information is either irrelevant or represented elsewhere.

## AGENT.md Update Direction

`AGENT.md` should be updated to become:

- a practical collaboration entrypoint
- a map to the current truth documents
- a short explanation of where execution history lives
- a fresh-session sync checklist

It should not try to duplicate every architecture document in full.

## README.md Update Direction

`README.md` should become the external-facing quick-start, while linking inward to:

- `AGENT.md`
- `conductor/index.md`
- key architecture docs

It should stay lighter than the full collaboration guide.

## Validation Criteria

This documentation initiative is successful when:

- `AGENT.md` clearly points to the right primary context files
- the repository has a stable fact set for architecture, UI, business capability, and open gaps
- a new session can restore context by following an explicit short sequence
- redundant or historical documents no longer compete with current-truth docs
- low-value file clutter is removed

## Risks

### Risk: creating another documentation layer

Mitigation:

- route everything back to a small number of primary documents
- keep execution artifacts separate from stable truth

### Risk: deleting something still useful

Mitigation:

- prefer archive over deletion unless the file is clearly low-value

### Risk: the new docs drift again

Mitigation:

- make `AGENT.md` and `conductor/index.md` explicitly state which files are primary truth
- update `conductor/tracks.md` when major context work lands

## Follow-up

Once this documentation spine is established, future project work should update:

- stable fact docs for long-lived truth changes
- execution docs for task-specific change history

That separation is the core maintenance benefit this initiative is meant to provide.
