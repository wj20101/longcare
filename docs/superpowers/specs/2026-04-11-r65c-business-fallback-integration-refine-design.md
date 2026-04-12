# R65C Business Fallback Integration Refined Design

## Related Docs

- index: [`../README.md`](../README.md)
- earlier design: [`2026-04-10-r65c-business-fallback-integration-design.md`](2026-04-10-r65c-business-fallback-integration-design.md)
- raw HID validation design: [`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)
- raw HID validation plan: [`../plans/2026-04-09-r65c-raw-hid-output-validation.md`](../plans/2026-04-09-r65c-raw-hid-output-validation.md)
- raw HID acceptance: [`2026-04-10-r65c-raw-hid-output-validation-acceptance.md`](2026-04-10-r65c-raw-hid-output-validation-acceptance.md)

## Context

LongCare already has a formal NFC business workflow centered on:

- [`NfcWorkflowScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt)
- [`NfcWorkflowViewModel`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt)
- [`NfcScanWorkflowDelegate`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt)

That workflow already supports two scan modes:

- `ScanMode.SYSTEM_NFC`
- `ScanMode.EXTERNAL_RFID`

It also already routes successful scans through a unified business event:

- `AppEvent.TagScanned(tagId, source)`

In parallel, the project now has two `R65C` test surfaces:

- the stable `R65C HID 键盘口测试` panel
- the diagnostic `R65C 原始 HID 输出验证` panel

Recent testing changed the integration decision:

- the smoke-test HID input path is stable enough for business fallback use
- the raw HID panel remains useful, but mainly as a diagnostic tool
- the business blocker is no longer “can `R65C` produce anything usable”
- the business blocker is “how do we feed `R65C` into the existing external-reader path without letting noisy HID input reach order actions”

## Goal

Use `R65C HID` as the formal fallback scan source when the device does not support NFC, while keeping the existing `EXTERNAL_RFID` business boundary and publishing only business-valid Tag IDs.

## Non-Goals

This refined design does not:

- replace system NFC on NFC-capable devices
- add a manual scan-mode switch to the workflow screen
- move the raw HID diagnostic panel into the business workflow
- create a separate order workflow just for `R65C`
- accept loosely normalized alphanumeric strings as business-valid IDs
- redesign the downstream order start or end flow

## Approved Direction

Keep the current workflow split:

- NFC-supported device -> `SYSTEM_NFC`
- NFC-unsupported device -> `EXTERNAL_RFID`

Within the `EXTERNAL_RFID` branch, treat `R65C HID` as an input source for the existing external-reader business boundary, not as a separate scan mode.

That means the refined design should preserve this downstream contract:

- valid fallback scan -> `AppEvent.TagScanned(validTagId, ScanSource.EXTERNAL_RFID)`

The workflow screen should still feel like one business page. The only difference on non-NFC devices is how the valid `tagId` enters the existing event flow.

## Design

### 1. Activation Rule

The scan-mode decision remains unchanged:

- if NFC is supported, use `SYSTEM_NFC`
- if NFC is not supported, use `EXTERNAL_RFID`

This continues to rely on [`selectScanMode(isNfcSupported)`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt).

No manual override is needed in the first version.

### 2. Input Boundary

`R65C HID` should not bypass the external-reader business boundary.

Instead, the integration should be structured like this:

- `NfcWorkflowScreen` hosts the non-NFC workflow UI
- a small `R65C HID` workflow adapter captures stable HID text input in formal workflow mode
- that adapter passes candidate text into the `EXTERNAL_RFID` boundary
- the external-reader boundary decides whether the candidate is business-valid
- only then does it publish `TagScanned(..., EXTERNAL_RFID)`

This keeps responsibilities clear:

- UI layer hosts input collection
- external-reader layer owns validation, duplicate suppression, and error escalation
- business workflow continues to consume only validated scan events

### 3. Business Validity Rule

The accepted `R65C` business value must match the system NFC Tag ID rule.

The current system NFC path ultimately uses:

- [`NfcIntentDataUtils.bytesToHexString(...)`](../../../app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt)

That means the business-valid format is:

- uppercase hexadecimal only
- no separators
- no whitespace
- same length profile as the real NFC Tag IDs used by current business flow

For the first version, the explicit acceptance rule is:

- allow only `0-9` and `A-F`
- after normalization, accept only lengths already observed as real business Tag IDs
- a known valid example is `0426FAFA051F91`

Any value that does not match this format is not business-valid and must not produce `TagScanned`.

### 4. Normalization and Stabilization

Before business validation, the candidate input should be normalized by:

- trimming outer whitespace
- removing inner whitespace and simple separators
- uppercasing the result

After normalization, the boundary should apply:

- strict hex-only validation
- valid-length filtering
- duplicate suppression within a short interval

The duplicate rule should prevent one physical card swipe from publishing several identical `TagScanned` events.

### 5. Invalid Input Policy

Invalid `R65C HID` input should not raise a user-facing error immediately.

Policy:

- one invalid input -> silently ignore
- repeated invalid inputs -> count toward a consecutive-failure threshold
- threshold reached -> promote reader state to `DeviceError`
- one valid input -> reset the consecutive-failure counter

Recommended first threshold:

- `3` consecutive invalid captures

This matches the desired UX:

- occasional noise does not spam the user
- persistent reader problems still become visible

### 6. Workflow Screen Behavior

When `scanMode == EXTERNAL_RFID` because NFC is unavailable:

- the page should enter a ready-to-scan state automatically
- the user should not need to tap a “start scanning” button
- the screen should describe placing the card on `R65C`
- existing order actions should remain unchanged

This keeps the user experience close to the current NFC workflow while still allowing a different scan source behind the scenes.

### 7. Reader UI State Semantics

The existing `ReaderUiState` model remains the right shape:

- `Disconnected`
- `Ready`
- `Reading`
- `DeviceError(message)`
- `NotRequired`

For the refined `R65C` fallback path, the intended meanings are:

- `NotRequired`: system NFC is active
- `Disconnected`: fallback mode is active but `R65C` is not ready for use
- `Ready`: non-NFC workflow is active and waiting for a valid `R65C` scan
- `Reading`: HID input is currently being assembled or stabilized
- `DeviceError`: repeated invalid captures or fallback reader failure

### 8. Relationship to Existing Test Surfaces

The current `R65C HID 键盘口测试` panel should not be copied into the business workflow UI.

Its role after this integration is:

- validate reader behavior during development
- provide confidence that the smoke-test HID path is still stable

The raw HID validation panel should also remain test-only.

Its role is:

- diagnose intermittent garbage input
- compare text-field capture with lower-level event interpretation
- support future `R65C` tuning without polluting the business flow

## Testing Strategy

### Unit Tests

Add tests around the fallback input stabilization layer for:

- valid normalized hex input publishes `TagScanned(..., EXTERNAL_RFID)`
- invalid hex or noisy input does not publish
- duplicate valid scans are suppressed
- three consecutive invalid inputs produce `DeviceError`
- one valid input resets the invalid counter

### Workflow Tests

Cover:

- `selectScanMode(false)` still resolves to `EXTERNAL_RFID`
- non-NFC workflow enters ready state automatically
- external reader state changes remain consistent
- downstream business flow remains unchanged after a valid external scan

### Manual Verification

Manual verification should confirm:

- a non-NFC device can complete the formal workflow through `R65C`
- a valid `R65C` scan produces the same Tag ID format as the NFC path
- intermittent garbage is ignored
- repeated garbage eventually produces a visible error state

## Consequences

This design intentionally keeps the business integration conservative.

That is the correct trade-off because:

- the project already has a working `EXTERNAL_RFID` business path
- `R65C` now looks stable enough to act as a fallback input source
- strict validation is safer than optimistic acceptance in an order workflow

The result is a small expansion of an existing business boundary, not a new workflow family.

## Next Step

If this refined design is approved, the next document should be an implementation plan that:

1. identifies the exact `R65C` fallback adapter and external-reader files to modify
2. defines the stabilization and validation rules as tests first
3. wires the non-NFC workflow screen into the existing `EXTERNAL_RFID` event path
