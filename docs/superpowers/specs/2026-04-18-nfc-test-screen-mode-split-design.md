# NfcTest Screen NFC/HID Mode Split Design

## Related Docs

- index: [`../README.md`](../README.md)
- HID keyboard test panel design: [`2026-04-07-r65c-hid-keyboard-test-panel-design.md`](2026-04-07-r65c-hid-keyboard-test-panel-design.md)
- raw HID validation design: [`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)
- business fallback UX fix: [`2026-04-11-r65c-business-fallback-ux-fix-design.md`](2026-04-11-r65c-business-fallback-ux-fix-design.md)

## Background

[`NfcTestScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt) currently mixes three concerns into one test page:

- NFC test flow
- `R65C HID` keyboard-style test flow
- raw HID output validation flow

That creates two problems for the current testing need:

1. The page shows redundant debug tooling even when the device capability already tells us which test path matters.
2. The current HID keyboard test relies on a visible text input, which causes the soft keyboard to appear and does not match the intended “reader-style” interaction model.

The new requirement is explicit:

- remove the redundant raw HID output validation from the test page
- detect whether the current device supports NFC
- if NFC is supported, keep the existing NFC test logic
- if NFC is not supported, show only the keyboard/HID test
- the HID test must provide a copy action for the final recognized result
- the test page must be portrait-only
- the HID interaction should follow the same no-soft-keyboard capture model already used in [`NfcWorkflowScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt)

## Goal

Refactor the NFC test page into a capability-driven two-mode screen so that:

- NFC-capable devices show only the NFC test flow
- non-NFC devices show only the HID keyboard test flow
- the raw HID validation panel no longer participates in the page
- HID testing behaves like a reader capture surface rather than a text-entry form
- the final recognized HID result can be copied directly
- the page is consistently locked to portrait orientation

## Non-Goals

This design does not:

- redesign the existing login-side hidden test-entry gate
- change the NFC business workflow in [`NfcWorkflowScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt)
- add persistence for test mode or copy history
- introduce a user-facing manual switch between NFC mode and HID mode
- preserve the raw HID validation panel inside the main test page for backward compatibility

## Current Problem Areas

### 1. `NfcTestScreen` always wires all test paths together

The current screen initializes:

- `NfcTestViewModel`
- `R65CHidInputTestViewModel`
- `R65CHidRawValidationViewModel`

and the content layer renders:

- the NFC card
- the HID keyboard panel
- the raw HID validation panel

This means the page ignores device capability and behaves like a combined diagnostics bench instead of a focused test surface.

### 2. Raw HID validation is redundant for the main page

The “原始 HID 输出验证” path is useful as low-level diagnostics, but it is no longer appropriate for the main NFC test page.

For this page, it adds:

- extra screen complexity
- extra state and event plumbing
- an additional debugging path that is not part of the primary operator flow

### 3. HID testing currently uses editable input UI

[`R65CHidInputTestPanel`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt) currently exposes an `OutlinedTextField` for input capture.

That causes two UX problems:

- the soft keyboard can appear
- the page behaves like a manual typing tool instead of a reader/listener surface

This is exactly the interaction model that was already corrected in the formal workflow by moving to a hidden HID capture surface.

### 4. The test page does not yet enforce portrait orientation

The project already uses `LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)` in multiple production screens, but the current NFC test page does not participate in that pattern.

## Approved Direction

Keep one test page, but split its behavior into two explicit modes based on real device capability.

- `supportsNfc == true`:
  show only the existing NFC test experience
- `supportsNfc == false`:
  show only the HID keyboard test experience

At the same time:

- remove raw HID validation from the page
- change HID capture from `TextField`-driven entry to hidden key-event capture
- add a dedicated copy action for the final recognized result
- lock the page to portrait

## Design

### 1. Mode Decision Rule

[`NfcTestScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt) should determine the mode at the UI entry layer using the device capability check already available in:

- [`NfcUtils.isNfcSupported(context)`](../../../app/src/main/kotlin/com/ytone/longcare/common/utils/NfcUtils.kt)

This mode decision belongs in the screen layer, not in the view model, because it is a direct UI composition concern:

- which test surface should be rendered
- which helpers should be bound
- which capture carrier should be attached

The test-entry session gate remains unchanged. This design only controls what the page does after the user is allowed to enter it.

### 2. Portrait Orientation Rule

The page should always lock to portrait orientation by reusing the existing project pattern:

- `LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)`

This should be applied at the top of [`NfcTestScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt), matching the way portrait-only screens are already implemented elsewhere in the app.

### 3. NFC Mode

When the device supports NFC:

- keep the existing NFC test card and helper-driven logic
- keep `NfcTestViewModel` and the helper lifecycle binding
- keep the tag dialog behavior

Do not show:

- `R65C HID` keyboard test UI
- raw HID validation UI

This mode should feel like the current NFC test experience, only cleaner because unrelated fallback tooling is gone.

### 4. HID Mode

When the device does not support NFC:

- show only the HID keyboard test UI
- do not bind `NfcTestViewModel` test-helper behavior
- do not show the NFC instruction card
- do not show the NFC tag dialog
- do not show raw HID validation

The HID mode becomes a focused reader-input test page rather than a multi-panel debug surface.

### 5. HID Capture Model

The HID mode should stop using a visible editable input field as the capture source.

Instead, it should follow the interaction pattern already established by:

- [`R65cWorkflowHidCaptureSurface`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidCaptureSurface.kt)

That means the HID mode should use:

- a hidden focusable capture surface
- automatic focus requests when the page is ready to scan
- explicit soft-keyboard hiding via `LocalSoftwareKeyboardController`
- key-event driven session assembly

The user should not need to:

- tap an input field
- manage focus manually before every scan
- dismiss the soft keyboard

The page should behave like “waiting for reader input,” not “waiting for text entry.”

### 6. HID Panel Role

[`R65CHidInputTestPanel`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt) should become a read-only status and result panel.

It should keep showing:

- current capture status
- latest raw input
- latest normalized UID
- latest completion time
- `重新聚焦`
- `清空结果`
- `复制结果`

It should stop showing:

- an editable `OutlinedTextField`

The visible panel becomes the operator-facing status view. The hidden capture surface becomes the technical input carrier.

### 7. Copy Result Behavior

The new `复制结果` action should copy only the final recognized result value.

The copy source is:

- `lastNormalizedUid`

The copy action should not use:

- the live input buffer
- the raw input string

Recommended behavior:

- enabled only when `lastNormalizedUid` is non-blank
- copies the normalized UID to the clipboard
- shows a short success toast after copy

If no valid normalized UID is available, the copy button should stay disabled instead of copying placeholder text.

### 8. ViewModel Responsibility

[`R65CHidInputTestViewModel`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt) should remain the owner of HID parsing state and completion state.

The responsibility should stay in the view model for:

- session state
- live accumulation state
- normalized UID result
- completion timestamp
- refocus/reset behavior

What changes is the input carrier:

- from text-field string entry
- to a dedicated HID key-event or session event API fed by the hidden capture surface

This keeps business logic and parsing state centralized while allowing the UI to move away from a soft-keyboard-driven interaction model.

### 9. Raw HID Validation Removal

The raw HID validation path should be removed from the main test page:

- no `R65CHidRawValidationPanel` in content
- no `R65CHidRawValidationViewModel` wiring in screen
- no host `onPreviewKeyEvent` bridge dedicated to raw validation

If the raw validation panel, contracts, view model, and tests become unused after this change, they should be deleted as dead code rather than left behind as dormant components.

This is important because the requirement is not just to hide raw validation, but to stop carrying redundant logic in the main test flow.

### 10. File Responsibilities

#### `NfcTestScreen.kt`

Should own:

- portrait locking
- `supportsNfc` decision
- mode-specific helper binding
- mode-specific capture-surface attachment

Should stop owning:

- raw HID validation wiring

#### `NfcTestScreenContent.kt`

Should own:

- rendering NFC mode or HID mode
- mode-specific body composition

Should stop owning:

- raw HID validation parameters and layout

#### `R65CHidInputTestPanel.kt`

Should own:

- HID status display
- operator actions
- copy button presentation

Should stop owning:

- editable input capture UI

#### `R65CHidInputTestContracts.kt` and `R65CHidInputTestViewModel.kt`

Should continue owning:

- stable representation of the final normalized result
- capture state
- refocus/reset signals

Should also move from text-change driven input to dedicated HID key or session event handling so the parsing boundary stays in the view model while the UI stops behaving like a form.

## Testing Strategy

### Unit Tests

Add or update unit coverage for HID result handling so that:

- the normalized result remains the authoritative copy source
- clearing state clears the copyable result
- refocus/reset behavior still works under the new carrier

If the view model receives dedicated key events after refactor, add coverage for:

- regular character capture
- session completion on terminator
- invalid result handling

### UI Tests

Add or update UI coverage so that:

- NFC mode shows NFC content and hides HID content
- HID mode shows HID content and hides NFC content
- raw HID validation is absent from the page
- the copy button is enabled only when a normalized UID exists

### Manual Verification

Manual verification must confirm:

- the page opens in portrait
- the page remains portrait while testing
- non-NFC devices do not show the soft keyboard on entry
- non-NFC devices do not show the soft keyboard while scanning
- HID scans still produce the expected normalized UID
- copy places the normalized UID into the clipboard
- NFC-capable devices still run the previous NFC test logic correctly

## Consequences

This design makes the test page smaller, more capability-driven, and more aligned with the real operator flow.

The key trade-off is that the page becomes less like a general diagnostics hub and more like a targeted test surface.

That trade-off is desirable because:

- it matches the actual device capability split
- it removes redundant debug burden from the operator path
- it reuses the proven HID interaction model already validated in the formal workflow
- it avoids soft-keyboard regressions in the non-NFC test path

## Next Step

If this design is approved, the implementation plan should:

1. remove raw HID validation from the NFC test page and delete dead code where appropriate
2. split [`NfcTestScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt) into NFC mode and HID mode based on device capability
3. lock the page to portrait
4. replace text-field HID capture with a hidden capture surface modeled on the workflow screen
5. convert the HID panel into a read-only status panel with copy support
6. add or update tests for mode rendering, result copyability, and raw-panel removal
