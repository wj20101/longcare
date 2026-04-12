# R65C Release And Merge Design

## Related Docs

- index: [`../README.md`](../README.md)
- refined fallback design: [`2026-04-11-r65c-business-fallback-integration-refine-design.md`](2026-04-11-r65c-business-fallback-integration-refine-design.md)
- fallback UX fix design: [`2026-04-11-r65c-business-fallback-ux-fix-design.md`](2026-04-11-r65c-business-fallback-ux-fix-design.md)
- reader-state fix design: [`2026-04-11-r65c-business-fallback-reader-state-fix-design.md`](2026-04-11-r65c-business-fallback-reader-state-fix-design.md)

## Context

The current local branch is:

- `codex/release-quality-gate-legacy-freeze-fix`

That branch already had an earlier pull request:

- `#45`

That pull request is already merged.

At the same time, the branch now contains a new set of local commits for:

- `R65C raw HID validation`
- `R65C business fallback integration`
- `R65C workflow UX fixes`
- related `docs/superpowers` documents

The repository’s primary upstream branch is currently:

- `origin/master`

The current release problem is therefore not “how to code the feature.” The release problem is:

> How do we safely package, push, review, and merge the new `R65C/NFC` work from a branch whose earlier PR has already been merged?

## Goal

Define a safe release and merge approach that:

- keeps using the current branch
- includes the current `nfc` code changes
- includes the current `docs/superpowers` document changes
- excludes unrelated local changes such as debug mock config drift
- uses a new pull request as the gate for CI/CD validation
- merges only after PR checks are green

## Non-Goals

This design does not:

- require a new branch
- require direct local merge into `master`
- require bundling unrelated workspace leftovers into the release
- redefine the `R65C` feature scope
- replace GitHub PR checks with manual judgment alone

## Approved Direction

Continue from the current branch, but treat this release as a new delivery unit.

That means:

- boundary-check the branch against `origin/master`
- stage only intended code and documentation
- explicitly exclude unrelated local changes
- push the current branch
- open a new PR from the same branch
- wait for CI/CD checks to pass
- merge through GitHub after checks are green

## Design

### 1. Release Scope

This release should include two categories only:

- `nfc` and fallback-related code changes
- `docs/superpowers/**` changes relevant to the current workstream

It should exclude:

- unrelated debug-only config changes
- unrelated local workspace leftovers

Known example to exclude:

- [`app/src/debug/assets/mock/system_config.json`](../../../app/src/debug/assets/mock/system_config.json)

### 2. Branch Strategy

The branch name stays the same.

The release does not need a new branch, because the user explicitly chose to continue on the current one.

However, the release should be treated as a new PR unit, not as a continuation of merged PR `#45`.

### 3. Boundary Check Before Push

Before pushing, the release flow should explicitly check:

- what commits exist on `HEAD` that are not on `origin/master`
- whether those commits belong to the intended `R65C/NFC` and `docs/superpowers` scope

This is a safety step to prevent accidental inclusion of stale or unrelated branch history.

### 4. Commit Organization

The intended release should be organized into two logical groups:

- code commits
- documentation commits

They do not need to be squashed into one commit, but the final pushed history should remain understandable in review.

The purpose of this split is:

- code review remains focused on behavior
- document review remains focused on design and traceability

### 5. Push And PR Flow

After the boundary check and local staging are complete:

1. push the current branch
2. create a new PR from this branch to `master`
3. verify what GitHub shows in the PR diff and commit list
4. use PR checks as the CI/CD release gate

### 6. CI/CD Success Rule

The release is not ready to merge until the PR checks are green.

If checks fail:

- investigate the failure
- distinguish real code issues from transient environment failures
- fix and push again

The merge decision should be made only after the PR reflects the intended scope and checks pass.

### 7. Merge Rule

Do not merge directly in local git.

Merge through the PR after:

- scope is confirmed
- checks are green

This keeps GitHub as the source of truth for release status and preserves CI/CD discipline.

## Risks

### 1. Already-merged branch confusion

Because the branch already had a merged PR, there is a risk that a new PR could surprise reviewers if commit scope is not checked first.

That is why the boundary check is mandatory.

### 2. Scope drift in docs

`docs/superpowers` currently contains multiple new and modified files. Some may not all belong equally to this release.

That is why document staging must be intentional, not blanket-add from the entire repository root.

### 3. CI noise vs real failure

This repository has already shown intermittent Gradle, Kotlin, and KSP cache noise during local verification.

That means CI triage should be careful:

- one flaky failure should be verified before changing source
- repeated deterministic failure should be treated as a real issue

## Success Criteria

This release plan is complete when:

- the intended `nfc` code and `docs/superpowers` docs are committed
- unrelated files are excluded
- a new PR from the current branch exists
- PR checks pass
- the PR is merged into `master`

## Next Step

If this design is approved, the next document should be an implementation plan that:

1. checks branch scope against `origin/master`
2. stages only intended code and docs
3. pushes the branch
4. creates a new PR
5. follows PR checks to green and merges
