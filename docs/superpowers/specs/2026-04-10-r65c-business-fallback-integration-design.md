# R65C Business Fallback Integration Design

## Context

LongCare already has a formal NFC workflow built around [`NfcWorkflowScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt) and [`NfcWorkflowViewModel`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt).

That workflow already contains an external-reader branch:

- [`ScanMode.SYSTEM_NFC`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt)
- [`ScanMode.EXTERNAL_RFID`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt)
- [`ExternalRfidReaderManager`](app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt)
- event consumption through [`AppEvent.TagScanned(tagId, source)`](core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt)

At the same time, the project now has two test surfaces for `R65C`:

- a smoke-test input panel based on the HID keyboard-input path
- a dedicated raw HID validation panel for diagnosing edge cases

Real-device testing established these points:

- `R65C` can be configured to emit the same UID string format that the system NFC path uses
- the existing HID keyboard-input capture path is correct most of the time
- some scans still produce intermittent garbage, including Chinese characters, repeated fragments, or overlong values

That changes the design decision.

The project no longer needs to block all business integration until raw HID diagnostics are perfect. Instead, the right design is:

- use the current HID text-capture path as the primary `R65C` business fallback input
- add a stabilizing and filtering layer before publishing business scan events
- keep the raw HID validation panel as a diagnostic companion for intermittent anomalies

## Goal

Integrate `R65C` into the formal NFC workflow as the automatic fallback when the device does not support NFC, while:

- preserving the existing system NFC workflow on NFC-capable devices
- publishing `AppEvent.TagScanned(..., ScanSource.EXTERNAL_RFID)` only for valid `R65C` input
- rejecting intermittent garbage input instead of letting it reach business order actions
- keeping the raw HID validation panel for diagnostic use in the test surface

## Non-Goals

This design does not include:

- replacing system NFC on devices that already support NFC
- exposing a manual scan-mode switch in the formal workflow
- making `R65C` available in parallel with system NFC on NFC-capable devices
- treating every HID text update as immediately business-valid
- removing the test-only raw HID validation panel
- adding support for arbitrary external readers beyond `R65C`
- changing the server-side meaning of `nfcDeviceId`

## Approved Direction

Use `R65C` only as the automatic fallback when `NfcUtils.isNfcSupported(context) == false`.

Continue to route all successful scans through the existing business event flow:

- `AppEvent.TagScanned(tagId, ScanSource.EXTERNAL_RFID)`
- `NfcScanWorkflowDelegate`
- existing start/end order logic

Do not integrate the raw HID validation panel into the business workflow. Keep it in the test surface as a diagnostic tool.

## Design

### 1. Activation Rule

Formal workflow activation should stay simple:

- NFC-capable device -> `SYSTEM_NFC`
- device without NFC support -> `EXTERNAL_RFID`

This continues to use the existing `selectScanMode(isNfcSupported)` decision path in [`NfcWorkflowContracts.kt`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt).

No user-facing scan-mode switch is needed in the first version.

### 2. Business-Equivalent Event Flow

When `scanMode == EXTERNAL_RFID`, a successful `R65C` read should be business-equivalent to a successful system NFC read.

That means the external-reader path should still terminate in:

- `AppEvent.TagScanned(validUid, ScanSource.EXTERNAL_RFID)`

The downstream business workflow should not need a separate branch for `R65C` once the input is validated.

This keeps the order workflow logic unified and avoids duplicating start/end-order behavior by scan source.

### 3. Input Source for Formal Workflow

Use the existing HID keyboard-input strategy as the primary source for `R65C` in the formal workflow.

Do not require raw key-event capture for the main business path.

Reason:

- current HID text capture already succeeds in most real scans
- the business need is reliable fallback behavior, not a protocol lab
- raw key-event validation remains useful for diagnostics, but it should not be required for the formal path to function

### 4. Add an Input Stabilizer in the External Reader Layer

The external-reader integration must not publish every raw HID text string as a business scan.

Add a stabilizing and filtering layer on the `R65C` path inside the external-reader manager boundary.

Recommended responsibilities:

- receive raw HID text input
- normalize it
- validate it
- reject malformed values
- suppress duplicates
- surface reader-state errors only after repeated failures

This layer should live inside the `ExternalRfidReaderManager` implementation path rather than in the business workflow layer.

### 5. Validation Rules for Business Publication

For the first version, only publish `TagScanned` when the candidate value passes all of these checks:

#### A. Normalization

Normalize the raw input by:

- trimming whitespace
- removing spaces and simple separators if present
- uppercasing the result

The existing [`ExternalRfidTagParser`](app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt) can continue to provide part of this normalization behavior.

#### B. Character Filter

Accept only uppercase hexadecimal characters:

- `0-9`
- `A-F`

Reject any value containing:

- Chinese characters
- punctuation that cannot be normalized away
- mixed garbage after normalization

#### C. Length Filter

For the first version, accept only lengths consistent with the observed card UID forms:

- 8 hex chars for 4-byte UID
- 14 hex chars for 7-byte UID

Everything else should be rejected.

#### D. Duplicate Suppression

Prevent one physical swipe from publishing multiple `TagScanned` events.

Use a small duplicate window keyed by the normalized UID, for example:

- same UID repeated within a short interval should be ignored

The exact timeout can be implementation-level, but it should be short enough not to block normal repeated scans.

### 6. Handling Intermittent Garbage

The workflow should not show an error for every single noisy HID capture.

Instead:

- reject invalid strings silently at first
- count consecutive invalid captures
- only surface a visible reader error after the invalid-capture count reaches a threshold

Recommended first threshold:

- 3 consecutive invalid captures

After a valid scan:

- reset the invalid-capture counter

This matches the desired UX:

- normal occasional noise does not spam the user
- persistent bad input still becomes visible

### 7. Reader UI State Semantics in Formal Workflow

The current [`ReaderUiState`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt) should be kept, but its `EXTERNAL_RFID` semantics should become more concrete.

Recommended meanings:

- `Disconnected`
  - reader unavailable or not ready for use
- `Ready`
  - fallback mode active and workflow is waiting for a valid `R65C` scan
- `Reading`
  - a candidate HID input session is in progress
- `DeviceError(message)`
  - repeated invalid scans, reader configuration problem, or external-reader failure
- `NotRequired`
  - system NFC is active instead

### 8. Formal Workflow UI Changes

Keep [`NfcWorkflowScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt) and its overall layout structure.

Do not add a new workflow screen.

When `scanMode == EXTERNAL_RFID`, update only the workflow copy and status presentation:

- prompt text should describe placing the card on `R65C`
- status text should describe reader readiness and retry state
- bottom business actions should remain the same

Recommended interaction model:

- page enters `Ready` automatically
- user can scan immediately
- provide a lightweight retry/refocus action as a recovery control

The page should feel like the same order workflow, not a separate debug tool.

### 9. Relationship to the Raw Validation Panel

The new raw HID validation panel stays in the test surface and remains part of the design.

Its role is:

- diagnose intermittent bad reads
- compare text-field output to lower-level event-derived interpretation
- help verify future reader configuration changes

It is not part of the business UI path.

### 10. Testing Strategy

#### Unit Tests

Add tests around the external-reader integration layer for:

- valid normalized UID publishes `TagScanned`
- invalid garbage does not publish `TagScanned`
- duplicate valid scans are suppressed
- invalid-capture counter trips `DeviceError` only after threshold
- valid scan resets the invalid-capture counter

#### Workflow-Level Tests

Cover:

- `scanMode == EXTERNAL_RFID` copy selection on non-NFC devices
- reader state transitions `Disconnected -> Ready -> Reading -> Ready`
- business event path remains unchanged after a valid external scan

#### Manual Verification

Manual verification should include:

1. non-NFC device enters external-reader mode automatically
2. valid `R65C` scan produces the same business outcome as system NFC
3. one swipe does not trigger multiple order actions
4. occasional garbage input does not immediately show user-facing error
5. repeated garbage input eventually surfaces an error and can recover

## Consequences

This design accepts a practical trade-off:

- the formal path uses the currently reliable HID text-capture strategy
- the raw HID validation path remains available for deeper diagnostics

That is appropriate because business needs are now narrower and better understood:

- most `R65C` scans are already correct
- the remaining problem is intermittent noise, not total incompatibility
- a stabilizing layer is a smaller and safer step than delaying all business fallback integration until perfect raw-event certainty exists
