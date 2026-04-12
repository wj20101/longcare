# R65C Raw HID Output Validation Review Design

## Related Docs

- index: [`../README.md`](../README.md)
- implementation plan: [`../plans/2026-04-09-r65c-raw-hid-output-validation.md`](../plans/2026-04-09-r65c-raw-hid-output-validation.md)
- original design: [`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)
- acceptance checklist: [`2026-04-10-r65c-raw-hid-output-validation-acceptance.md`](2026-04-10-r65c-raw-hid-output-validation-acceptance.md)

## Context

The current workspace contains a partially completed implementation of the host-driven `R65C 原始 HID 输出验证` refactor.

The active edits already touch the expected files:

- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt)
- related tests under `app/src/test` and `app/src/androidTest`

At the same time, the work is not yet closed out:

- there are still unstaged and uncommitted changes in the workspace
- the implementation plan has not been verified task by task
- tests and final verification have not yet been confirmed

The immediate need is not a new feature design. The immediate need is a structured review of the current edit set against the approved plan so the team can finish the work cleanly.

## Goal

Define a review pass for the current `R65C raw HID output validation` implementation that:

- compares the active edits against the approved plan
- identifies where the implementation already matches the plan
- identifies where it deviates, is incomplete, or remains risky
- recommends a practical completion sequence

This review is a pre-completion audit. It exists to reduce drift before more code is added.

## Non-Goals

This review design does not:

- redefine the host-driven raw HID validation feature
- introduce new product requirements
- expand the NFC test screen beyond the approved scope
- replace the original implementation plan
- perform the code changes itself

## Approved Direction

Treat the next step as a structured review, not immediate continued coding.

The review should take the current workspace as-is, compare it against the approved plan, and produce a result with two outputs:

1. a clear list of findings
2. a recommended completion path

This keeps the team from continuing implementation while key mismatches remain ambiguous.

## Review Inputs

The review should use three input sources together.

### 1. Plan source of truth

Use the implementation plan as the reference for intended scope, file ownership, and expected verification steps:

- [`../plans/2026-04-09-r65c-raw-hid-output-validation.md`](../plans/2026-04-09-r65c-raw-hid-output-validation.md)

### 2. Original feature design

Use the original feature design to interpret intent where the plan is terse:

- [`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)

### 3. Current workspace edits

Use the current edited files and related tests as the source of actual implementation state.

## Review Scope

The review should cover the same functional boundary as the original feature work:

- raw HID state contracts
- host-level key capture adapter
- raw validation ViewModel lifecycle
- raw validation panel behavior
- `NfcTestScreen` and `NfcTestBody` wiring
- unit tests and android tests tied to this flow

The review should ignore unrelated workspace edits unless they directly affect this feature.

## Review Criteria

The review should judge the current implementation on five dimensions.

### 1. Plan alignment

Check whether responsibilities match the plan.

Examples:

- host-level raw capture should live at screen level, not inside the panel
- the panel should act as display and control surface, not capture authority
- the ViewModel should own listening lifecycle and session completion
- the smoke-test panel should remain present

### 2. Behavioral alignment

Check whether actual runtime behavior matches the intended semantics.

Examples:

- `Idle`, `Armed`, `Capturing`, and `Completed` should mean what the plan says they mean
- stopping listening should prevent further raw session capture
- text-field behavior should remain comparison-only
- clearing a session should return to the correct listener state

### 3. Test alignment

Check whether tests cover the plan’s critical transitions and guarantees.

Examples:

- host adapter filtering
- listening start and stop behavior
- session completion by `Enter` and idle timeout
- panel control visibility and callbacks
- screen or body-level regression wiring

### 4. Risk level

Each finding should be classified by delivery risk:

- high risk: likely to break intended behavior or final verification
- medium risk: main direction is correct but an important gap remains
- low risk: cleanup, consistency, or closure work

### 5. Completion priority

Each recommendation should be ordered so the team fixes correctness first, testability second, and polish last.

## Expected Review Output

The review should produce a fixed-structure result.

### A. Review summary

A short summary should answer:

- Is the implementation broadly on-plan or off-plan?
- What parts are already in the correct direction?
- What parts must be resolved before final verification?

### B. Already aligned with plan

List the parts that should not be reworked unless a defect is found.

### C. Deviations or incomplete areas

Each finding should be written in this structure:

- finding
- evidence from current code or tests
- why it does not fully match the plan
- risk level
- recommended fix

### D. Recommended finish order

The review should end with a smallest-safe completion order.

Recommended default sequence:

1. contracts and ViewModel behavior
2. host adapter and screen wiring
3. panel behavior and UI test coverage
4. final verification commands and document closure

### E. Completion standard

The review should restate what must be true before the task is considered done:

- implementation responsibilities match the plan
- critical tests exist and pass
- verification commands pass
- the feature can be checked against the acceptance checklist

## Consequences

This review design adds one explicit checkpoint before more implementation work continues.

That is the correct trade-off because:

- the workspace already contains partial implementation
- continuing blindly risks drift between code and plan
- a review-first pass makes the remaining work smaller and safer

Once this review is complete, the next document should be a focused implementation plan for closing the identified gaps, not a new feature design.

## Next Step

After this review design is approved:

1. write the concrete review findings against the current workspace
2. convert the findings into a close-out plan
3. resume implementation only after the completion order is clear
