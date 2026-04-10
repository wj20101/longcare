# R65C Raw HID Output Validation Design

## Context

LongCare now has an `R65C HID 键盘口测试` panel inside the NFC test screen:

- route: [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt)
- panel UI: [`R65CHidInputTestPanel`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt)
- test-only state and capture logic:
  - [`R65CHidInputTestContracts.kt`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt)
  - [`R65CHidInputTestViewModel.kt`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt)

That panel proved two important facts:

- `R65C` can produce readable HID keyboard-style output
- the current app-side capture path can observe that output

It also has an important limitation:

- the current panel is built on text-input callbacks from `OutlinedTextField`
- it is useful as a smoke test
- but it is not authoritative evidence of the reader's raw HID output because the visible text may already be altered by IME, keyboard-layout, or text-input handling

At the same time, real-device testing exposed a more important question before business integration can proceed:

- some `R65C` output settings can produce a full NFC UID that matches system NFC output
- other settings can produce truncated, transformed, or polluted values
- some captures can contain unexpected characters, including Chinese text, suggesting IME or keyboard-mapping interference

The current business workflow does not consume a full Android `Tag` object. It consumes a string `tagId`:

- system NFC path publishes `AppEvent.TagScanned(tagId, ScanSource.SYSTEM_NFC)` from [`NfcManager.kt`](app/src/main/kotlin/com/ytone/longcare/common/utils/NfcManager.kt)
- the `tagId` is currently derived from `tag.id` as an uppercase hexadecimal string via [`NfcIntentDataUtils.bytesToHexString(...)`](app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt)
- the formal NFC workflow later passes that same string as `nfcDeviceId`

That means the real blocker is not “missing Tag object support.” The blocker is narrower:

> What does `R65C` actually emit at the raw HID key-event level, and can that output be trusted and normalized into the same `tagId` string format that the system NFC path already uses?

This sub-project exists to answer that question before formal workflow integration.

## Goal

Add a dedicated `R65C 原始 HID 输出验证` test panel so developers can inspect:

- the text-field result currently visible to the user
- the underlying key-event stream produced during one card swipe
- a character sequence assembled directly from raw key events
- candidate normalized values derived from the same swipe
- whether the output looks like:
  - a full UID
  - a 4-byte card number
  - a transformed encoding
  - or IME / keyboard-mapping pollution

## Non-Goals

This design does not include:

- integrating `R65C` into the formal `NfcWorkflowScreen`
- publishing `AppEvent.TagScanned` from this new validation panel
- changing the production sign-in or sign-out flow
- replacing the existing `R65C HID 键盘口测试` panel until the new validator is proven useful
- building a generic HID framework for arbitrary devices
- supporting Bluetooth readers
- packet-level USB Host analysis

## Approved Direction

Keep this work inside the existing NFC test surface and add a new validation panel focused on raw HID output inspection.

Do not move directly into production integration yet.

The new validation path should:

- capture raw key events at the test-screen host level instead of trusting a text field
- still preserve the text-field result as a comparison layer
- group one swipe into a single completed session
- generate several candidate interpretations from the same session
- make it obvious when host-level key-event output and text-field output diverge

The design explicitly treats the existing `R65C HID 键盘口测试` panel as:

- a smoke-test surface
- not the source of truth for raw reader output

## Design

### 1. Screen Structure

Reuse [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt).

The screen should continue to serve as the debug and exploration surface for NFC-adjacent input work.

Within that screen, add a new panel labeled:

- `R65C 原始 HID 输出验证`

This panel should live alongside the existing `R65C HID 键盘口测试` panel rather than replacing it immediately. The existing panel remains useful as a quick smoke test, while the new panel becomes the diagnostic truth source.

### 2. New Units and Responsibilities

The new validation feature should be split into three focused units.

#### `R65CHidRawValidationViewModel`

A dedicated test-only view model for raw HID inspection.

Responsibilities:

- collect and store raw key events for one swipe session
- maintain a plain text-field value for comparison
- assemble a character stream from raw key events
- close a session by `Enter` or idle timeout
- derive candidate interpretations from the completed session

It should not:

- emit production `TagScanned` events
- mutate formal workflow state
- assume any candidate value is correct enough for business use

#### `R65CHidRawValidationPanel`

A dedicated composable panel rendered inside `NfcTestScreen`.

Responsibilities:

- render the current validation state
- show text-field output versus host-captured event output
- show recent key events
- show candidate interpretations for the same swipe
- expose a small set of explicit controls such as:
  - `开始监听`
  - `停止监听`
  - `清空会话`

It should not be the authority for raw key capture.

#### `R65CHidRawCaptureHost`

A small host-side capture adapter that lives at the `NfcTestScreen` layer.

Responsibilities:

- receive raw `KeyEvent` values from the test screen host
- forward only relevant events into the validation ViewModel
- activate only while the raw validation flow is armed

It should not:

- contain candidate interpretation logic
- format user-facing diagnostic copy

#### `R65CHidRawValidationState`

A state model specific to the validation workflow.

Responsibilities:

- track whether the panel is waiting for focus, receiving keys, completed, or errored
- keep current-session and last-session data separate

### 3. UI Blocks

The panel should include five blocks.

#### A. Capture Status

Show states such as:

- idle
- listening
- receiving raw keys
- session completed
- capture error

#### B. Text-Field Result

Show the current text value produced by the input surface.

This is intentionally kept even though it is not the authoritative layer. It lets us compare “what the field ended up showing” with “what the key events actually contained.”

#### C. Event-Assembled Output

Show the string assembled from host-captured raw key events.

This is the most important block in the panel because it helps distinguish:

- device output issues
- IME or keyboard-layout interference
- or app-side session handling problems

#### D. Key Event Log

Show the last completed session’s key-event list, including at least:

- `keyCode`
- `unicodeChar`
- `action`

The first version does not need a scrollable history across many sessions. The latest completed session is enough.

#### E. Candidate Interpretations

For one completed swipe, generate and display several candidate values, such as:

- raw string as-is
- hexadecimal-only filtered string
- decimal-to-hex conversion
- reversed 4-byte hexadecimal candidate
- simple “looks like 8 hex / 14 hex / invalid” classification

The panel should not guess a single final answer yet. It should show candidates so the team can compare them to a known NFC tool reading.

### 4. State Model

Recommended shape:

- `R65CHidRawCaptureState.Idle`
- `R65CHidRawCaptureState.Armed`
- `R65CHidRawCaptureState.Capturing`
- `R65CHidRawCaptureState.Completed`
- `R65CHidRawCaptureState.CaptureError`

Recommended data fields:

- `textFieldValue`
- `currentSessionEvents`
- `currentSessionAssembledChars`
- `lastSessionTextFieldValue`
- `lastSessionAssembledChars`
- `lastSessionEvents`
- `lastCompletedReason`
- `candidateValues`
- `lastCompletedAt`
- `isListening`

This keeps “what is happening now” separate from “what was captured last time.”

### 5. Capture Logic

The validation workflow should not rely on a composable text field as the source of truth.

Instead, it should capture two layers in parallel:

#### Text layer

Use the input field’s text callback to preserve the final visible text.

#### Host key-event layer

Use a host-level key-event path from the test screen container, not the text field, to record raw key events directly.

For each relevant key event, record:

- `keyCode`
- `unicodeChar`
- `action`
- native event time if available

The view model should assemble a character sequence directly from those host-level key events for comparison.

The host adapter should explicitly ignore obvious non-scan keys such as:

- back
- volume keys
- navigation keys
- other system controls

### 6. Session Completion Rules

One swipe session should end by either:

1. `Enter` / newline key
2. idle timeout

Use the same rough timeout already proven useful in the test panel, about `400ms`.

The timeout should reset each time a new relevant key arrives.

### 7. Candidate Generation

When one session completes, generate candidate values using only simple, explicit rules:

- original event-assembled string
- original text-field string
- hex-only filtered candidate
- decimal-to-hex candidate if the string is numeric
- reversed-byte candidate for a 4-byte decimal-derived value if applicable
- classification label:
  - `looks like 8 hex`
  - `looks like 14 hex`
  - `numeric only`
  - `contains non-ASCII`
  - `invalid for business UID`

This feature is diagnostic. It should explain what the observed value resembles, not silently transform it into a production identifier.

### 8. Listener Lifecycle

The raw validation flow should not behave like a global keyboard interceptor.

Rules:

- default state is `Idle`
- the tester explicitly enters listening mode by tapping `开始监听`
- while listening, host-level key events are captured
- tapping `停止监听` or leaving the screen stops capture immediately
- the panel may request focus for the comparison text field, but focus is no longer what grants raw capture authority

This keeps the capture path scoped to the test screen and avoids accidental interference with unrelated key handling.

### 9. Interpreting Failures

The panel should help distinguish three failure classes.

#### A. Device-output issue

If the key-event layer itself contains the unexpected characters, the reader configuration or keyboard-mode behavior is suspect.

#### B. IME / text-input interference

If the key-event layer looks sane but the text-field layer contains garbage, the system input stack is likely interfering.

#### C. Business-incompatible output

If the output is internally consistent but still does not match the NFC-tool UID format, the reader may be emitting a shortened or transformed identifier rather than the full UID.

### 10. Testing Strategy

#### ViewModel tests

Cover:

- host-event collection into one session
- `Enter` completion
- idle-timeout completion
- listener arm / stop behavior
- event-assembled string generation
- candidate generation for:
  - hex-looking input
  - numeric-only input
  - non-ASCII / polluted input

#### UI tests

Cover:

- listening controls
- text-field block visibility
- key-log block visibility
- candidate-value block visibility

#### Manual validation

Manual validation should use the same physical card and compare:

1. system NFC tool UID
2. text-field value
3. host-captured event-assembled value
4. candidate values

The expected outcome is not “always produce a business-ready answer.” The expected outcome is “make the mismatch visible and diagnosable.”

## Consequences

This design intentionally delays formal workflow integration.

That is the correct trade-off right now because:

- the team has already proven that `R65C` can emit something readable
- the remaining uncertainty is about output truth and compatibility
- production integration before validating raw output would create ambiguous failures in the sign-in flow

Once this sub-project proves that `R65C` can stably emit a business-compatible UID, the next spec can safely describe formal integration into `NfcWorkflowScreen`.
