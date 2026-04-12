# R65C HID Keyboard Test Panel Design

## Context

LongCare already has a debug entry on the login screen and an existing NFC test route:

- the login screen exposes debug buttons through [`LoginNfcTestButtons`](app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginNfcTestButtons.kt)
- the app already routes to [`NfcTestRoute`](app/src/main/kotlin/com/ytone/longcare/navigation/NavigationRoutes.kt)
- the current test screen is [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt)

The current branch also contains an exploratory `Type-C USB Host` probe panel. That work assumed the external reader might be treated as a generic USB device that Android could enumerate, inspect, and read from directly.

The new device information changes that assumption.

`R65C` works in its default mode as a driver-free HID keyboard-emulation reader:

- it behaves like an external keyboard
- it outputs the card UID into the focused input target
- it does not provide a dedicated SDK or vendor API for Android integration
- it is suitable for UID capture, not generic USB protocol probing

That means the next useful step is no longer a general USB Host diagnostic panel. The next useful step is a focused test panel that answers a narrower question:

> Can the app receive R65C keyboard-style input reliably, detect when one scan is complete, and show both the raw input and the normalized UID?

## Goal

Replace the current Type-C USB Host probe panel on `NfcTestScreen` with an `R65C HID 键盘口测试` panel so developers and testers can verify:

- whether the focused input box receives keyboard-style output from `R65C`
- whether one scan can be completed by `Enter` or idle-time fallback
- what the raw input looked like
- what normalized UID the app derived from that raw input
- whether the panel can automatically reset and prepare for the next scan

## Non-Goals

This design does not include:

- keeping the current generic `USB Host Probe` panel alongside the new one
- a new standalone route just for `R65C`
- integration with the production NFC sign-in workflow
- automatic publishing of `TagScanned` business events from the test page
- support for Bluetooth readers
- support for other keyboard-emulation readers
- USB device enumeration, permission requests, or endpoint inspection
- vendor-specific command sets, SDK integration, or card-content reads
- a scan history list beyond the latest completed result

## Approved Direction

Reuse the existing `NfcTestScreen` route and replace the current `Type-C USB Host` panel with an `R65C`-specific HID keyboard-input test panel.

Do not preserve a compatibility branch for the old USB Host test flow in this screen. The user only needs to support `R65C` now, so the page, state names, and behavior should all be scoped to that device model and work mode.

Use a focused input-box strategy:

- a dedicated text input receives `R65C` keyboard-style output
- scan completion is detected by `Enter` first, then by a short idle timeout
- the panel keeps the last completed raw input and normalized UID
- after one scan completes, the live buffer clears and the panel requests focus again

Do not emit production business events from this page.

## Design

### 1. Screen Structure

Keep [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt) as the route and top-level screen.

The screen should contain two sections:

- the existing built-in NFC test card
- a new `R65C HID 键盘口测试` card

The new card replaces the current `Type-C USB Host` panel. The screen should no longer present:

- `刷新设备`
- `申请权限`
- `开始尝试读取`
- USB device details
- endpoint summaries
- raw USB payload views

This keeps the route stable while aligning the screen with the only device the team now cares about.

### 2. New Units and Responsibilities

The feature should be split into three focused units.

#### `R65CHidInputTestViewModel`

A dedicated test-only view model for the `R65C` panel.

Responsibilities:

- hold the live input buffer while characters are arriving
- detect scan completion by `Enter` or idle timeout
- keep the latest raw input, normalized UID, and completion time
- expose a screen-friendly state model for the panel
- request that the UI refocus the input box after a completed scan or manual reset

It should not:

- emit production `TagScanned` events
- depend on USB Host APIs
- manage Android focus primitives directly

#### `R65CHidInputTestPanel`

A composable panel rendered inside `NfcTestScreen`.

Responsibilities:

- render the current keyboard-input test state
- provide the dedicated input field for `R65C`
- show live input while a scan is in progress
- show the latest completed raw input and normalized UID
- expose only the small set of actions needed for this flow

#### `R65CHidPanelState`

A panel-state model dedicated to the keyboard-input test flow.

Responsibilities:

- describe whether the panel is waiting for focus, ready, receiving input, or showing the latest result
- separate current input activity from the latest completed result

This unit should replace the current `UsbProbeUiState` and `TypeCRfidPanelState` usage inside the test screen path.

### 3. UI Sections

The new `R65C` panel should include four blocks.

#### A. Status Area

This is the top summary area.

Show states such as:

- waiting for focus
- ready for scan
- receiving keyboard input
- last capture succeeded
- last capture failed

This should be the most prominent area because it tells the tester whether the page is actually ready to receive `R65C` output.

#### B. Input Area

Show a dedicated text field that acts as the capture target for `R65C`.

This field is not meant for manual typing as the primary workflow. Its purpose is to:

- receive keyboard-style input from the reader
- show the live buffer while a scan is in progress
- regain focus after a completed scan

If the field loses focus, the panel should make that clear and provide an easy way to recover.

#### C. Latest Result Area

Show the latest completed scan result with at least:

- raw input
- normalized UID
- completion time

The panel should keep the latest result visible after completion even though the live buffer is cleared for the next scan.

#### D. Action Area

The first version should support only:

- `重新聚焦`
- `清空结果`

These actions fit the keyboard-input model better than the old USB Host controls.

### 4. State Model

Use a dedicated keyboard-input panel state rather than reusing the production reader state or the old USB probe state.

Recommended shape:

- `R65CHidPanelState`
  - `captureState: R65CHidCaptureState`
  - `liveInputBuffer`
  - `lastRawInput`
  - `lastNormalizedUid`
  - `lastCompletedAt`
  - `focusRequestToken`

Where `R65CHidCaptureState` is one of:

- `R65CHidCaptureState.WaitingForFocus`
- `R65CHidCaptureState.ReadyForScan`
- `R65CHidCaptureState.ReceivingInput`
- `R65CHidCaptureState.LastCaptureSucceeded`
- `R65CHidCaptureState.LastCaptureFailed`

Alongside that state, keep separate fields for:

- `liveInputBuffer`
- `lastRawInput`
- `lastNormalizedUid`
- `lastCompletedAt`
- `focusRequestToken`

This separation matters:

- the capture state answers “what is the panel doing now?”
- the data fields answer “what was the last completed scan?”

### 5. Scan Completion Rules

One completed scan should be detected with a two-step rule:

1. prefer `Enter` or newline as the completion signal
2. if no completion key arrives, fall back to idle timeout

Use a short idle timeout of about `400ms`.

This supports both:

- readers that append `Enter` after the UID
- readers that stop after the UID without a terminator

The timeout should reset each time a new character arrives.

### 6. Completion Pipeline

When one scan completes:

1. copy the current buffer into `lastRawInput`
2. trim line breaks and irrelevant whitespace
3. pass the cleaned string through the existing parser normalization path
4. store the result in `lastNormalizedUid`
5. store `lastCompletedAt`
6. clear `liveInputBuffer`
7. request focus again for the next scan

If normalization produces no UID, the panel should treat the capture as failed while still preserving the raw input for debugging.

### 7. Error Handling and Edge Cases

Keep error handling simple and specific to this flow.

- If the input field is not focused, show `WaitingForFocus`
- If the panel receives characters, switch to `ReceivingInput`
- If completion occurs but normalization yields no UID, switch to `LastCaptureFailed`
- If the user clears results, remove the latest completed values and return to a focus-ready state
- If the user taps elsewhere and the field loses focus, expose `重新聚焦` as the recovery path

Do not add background listeners, global key interception, or page-wide keyboard capture in the first version.

### 8. Testing Strategy

Automated coverage should focus on the behavior that makes the feature trustworthy.

#### ViewModel tests

Cover:

- appending characters into `liveInputBuffer`
- completion by `Enter`
- completion by idle timeout
- clearing the live buffer after completion
- preserving `lastRawInput`
- preserving `lastNormalizedUid`
- failure path when normalization yields no UID

#### State-mapping tests

Cover:

- waiting for focus
- ready for scan
- receiving input
- last capture succeeded
- last capture failed

#### UI tests

Cover:

- `重新聚焦` action wiring
- `清空结果` action wiring
- live input and latest result being shown as separate values

### 9. Manual Verification

Manual verification should cover the real device path on Android:

1. open the test page and confirm the input field is ready or can be refocused easily
2. scan one card and confirm live characters appear
3. verify a reader that sends `Enter` completes immediately
4. verify a reader without `Enter` completes after idle timeout
5. confirm the latest raw input and normalized UID are both visible
6. confirm the live buffer clears and the field is ready for the next scan

## Consequences

This design intentionally retires the current generic `USB Host Probe` direction from this test screen.

That trade-off is correct for the current goal:

- the screen becomes more accurate for the real target device
- the naming becomes clearer
- the implementation gets smaller and easier to verify

The cost is that the route no longer serves as a generic external-reader probe surface. That is acceptable because the current requirement is explicit: only `R65C` needs to be supported now.
