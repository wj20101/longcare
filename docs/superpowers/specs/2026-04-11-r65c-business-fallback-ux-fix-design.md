# R65C Business Fallback UX Fix Design

## Related Docs

- index: [`../README.md`](../README.md)
- refined fallback design: [`2026-04-11-r65c-business-fallback-integration-refine-design.md`](2026-04-11-r65c-business-fallback-integration-refine-design.md)
- earlier fallback design: [`2026-04-10-r65c-business-fallback-integration-design.md`](2026-04-10-r65c-business-fallback-integration-design.md)
- raw HID validation design: [`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)

## Context

The recent `R65C` fallback integration established the right business direction:

- non-NFC devices use `ScanMode.EXTERNAL_RFID`
- validated fallback scans still publish `AppEvent.TagScanned(..., ScanSource.EXTERNAL_RFID)`
- downstream order flow stays unified

However, device testing exposed three UX problems in the formal workflow screen:

1. User-facing copy mentions `R65C`, which is too technical for end users.
2. Entering [`NfcWorkflowScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt) causes the soft keyboard to appear.
3. Swiping the card during fallback recognition also causes the soft keyboard to appear.

That means the business boundary is mostly correct, but the interaction model is wrong.

The formal workflow page should feel like “scan with a reader,” not “type into a hidden text field.”

## Goal

Fix the formal fallback UX so that:

- the workflow page no longer exposes technical `R65C` wording
- the workflow page does not trigger the system soft keyboard
- `R65C HID` fallback input is still captured reliably
- the existing external-reader business boundary remains in place

## Non-Goals

This UX-fix design does not:

- undo the existing `EXTERNAL_RFID` business integration
- remove strict fallback validation, duplicate suppression, or invalid-streak handling
- move raw HID diagnostics into the formal workflow
- add a user-facing scan-mode switch
- redesign the order workflow layout outside the fallback interaction path

## Approved Direction

Keep the existing business boundary, but replace the workflow-side HID input carrier.

That means:

- preserve `ExternalRfidReaderManager.submitHidCandidate(...)`
- preserve strict fallback validation and event publication rules
- remove the `TextField`-style input approach from the formal workflow page
- replace it with page-level HID key-event capture and a lightweight session collector
- update workflow copy to refer to a generic reader or scanning device, not `R65C`

## Design

### 1. User-Facing Copy Rule

Formal workflow copy should not expose hardware model names such as `R65C`.

The page may use terms such as:

- `读卡器`
- `识别设备`
- `感应区`

It should not assume the user understands the device model or scanning protocol.

Examples of acceptable phrasing:

- `请将卡片放在读卡器感应区`
- `请在识别设备上完成刷卡`

Examples of unacceptable phrasing:

- `请将卡片放在 R65C 感应区`
- `请确认 R65C 已连接`

### 2. Input Model Rule

The formal workflow page should not rely on a `TextField` or any UI element whose normal behavior implies text entry and soft-keyboard interaction.

Instead:

- page-level or screen-host-level `KeyEvent` capture should be used
- only the fallback path should consume these events
- the user should not see an editable input control
- the user should not be asked to tap an input field or manage focus manually

This aligns the page with the actual mental model of the task: scanning a card on a reader.

### 3. Lightweight HID Session Collector

The formal workflow should use a smaller and more focused session collector than the raw HID diagnostic tooling.

Its responsibilities should be limited to:

- accept relevant `ACTION_DOWN` key events
- ignore obvious non-scan system keys
- build a character buffer
- finish a session on `Enter` or short idle timeout
- emit one stabilized candidate string

It should not:

- expose raw event diagnostics in the business page
- compute candidate interpretations for display
- behave like a debug panel

The session collector exists only to convert one swipe into one business candidate string.

### 4. Boundary Preservation

Once the collector produces a stable candidate string, it should still forward that string into:

- `ExternalRfidReaderManager.submitHidCandidate(...)`

This is important because the existing external-reader boundary already owns:

- strict Tag ID validation
- duplicate suppression
- invalid-streak escalation
- business event publication

The UX fix should not duplicate those rules in the page layer.

### 5. Activation Scope

Page-level HID capture should be active only when:

- `scanMode == EXTERNAL_RFID`

It should not interfere with:

- `SYSTEM_NFC`
- unrelated screen-level navigation keys
- non-fallback workflow states

### 6. Reader State Semantics

The `ReaderUiState` model stays valid:

- `Disconnected`
- `Ready`
- `Reading`
- `DeviceError`
- `NotRequired`

What changes is the trigger path:

- `Reading` should now represent active reader-side recognition or HID session assembly
- not “user is typing into a hidden input”

### 7. Relationship to Test Surfaces

The existing test surfaces remain useful and separate:

- `R65C HID 键盘口测试` still validates smoke-test keyboard-style input
- `R65C 原始 HID 输出验证` still diagnoses low-level anomalies

The formal workflow should not import their UI directly.

It may reuse ideas or helper logic, especially host-level key filtering, but the business page should stay visually clean and user-facing.

## Testing Strategy

### Unit Tests

Add coverage for the formal workflow HID collector:

- non-scan keys are ignored
- regular scan characters are collected
- `Enter` completes a session
- idle timeout completes a session
- one session emits one candidate string

### Workflow Tests

Verify that:

- fallback mode still works on non-NFC devices
- no `TextField`-driven interaction is required
- copy no longer includes `R65C`
- validated external scan flow remains unchanged after candidate submission

### Manual Verification

Manual verification must confirm:

- entering the workflow page no longer shows the soft keyboard
- scanning on the reader no longer shows the soft keyboard
- the page copy is understandable to end users
- a valid card still drives the correct business flow

## Consequences

This design keeps the current business integration but changes the interaction carrier.

That is the correct trade-off because:

- the current business boundary is already useful
- the current UX is the real problem
- replacing the carrier is smaller and safer than redoing the fallback flow

## Next Step

If this UX-fix design is approved, the next document should be an implementation plan that:

1. removes the workflow `TextField` HID collector
2. adds page-level HID key-event capture plus a lightweight session collector
3. updates the fallback copy to remove `R65C` naming
4. verifies keyboard suppression and business-event continuity
