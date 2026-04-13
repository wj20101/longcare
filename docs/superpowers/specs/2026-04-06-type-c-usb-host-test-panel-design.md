# Type-C USB Host Test Panel Design

## Context

LongCare already has a test entry on the login screen and an existing NFC test route:

- the login screen exposes debug buttons through [`LoginNfcTestButtons`](app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginNfcTestButtons.kt)
- the app already routes to [`NfcTestRoute`](app/src/main/kotlin/com/ytone/longcare/navigation/NavigationRoutes.kt)
- the current test screen is [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt)

At the same time, the new Type-C RFID fallback work has added the first layer of generic USB reader plumbing, but the real device still does not provide an SDK and its protocol is unknown.

That means the next useful step is not business integration. The next useful step is a test panel that helps answer a narrower question:

> Can Android USB Host mode detect this Type-C reader, request permission, inspect its interfaces and endpoints, and read any raw payload without vendor-specific SDK support?

This test feature should stay inside the existing test surface, so the team can iterate on hardware probing without polluting the main workflow.

## Goal

Add a Type-C USB Host test panel to the existing NFC test screen so developers and testers can verify:

- whether a Type-C reader is detected as a USB device
- whether USB permission can be granted
- what device, interface, and endpoint information Android exposes
- whether a generic USB read attempt returns any raw payload
- whether the existing generic tag parser can extract a card ID from the raw payload

## Non-Goals

This design does not include:

- a new standalone route just for Type-C testing
- integration with the production NFC sign-in workflow
- automatic publishing of `TagScanned` business events from the test page
- support for Bluetooth readers
- support for keyboard-emulation readers
- vendor-specific command sets or SDK integration
- a full USB protocol analyzer or packet capture tool

## Approved Direction

Reuse the existing `NfcTestScreen` and add a second test panel dedicated to Type-C USB Host probing.

Do not create a separate route. Keep the current login-page test-entry pattern intact and let the NFC test page become the single place for both:

- built-in NFC test behavior
- Type-C USB Host exploratory testing

Use a general-purpose USB Host strategy in the first version:

- enumerate devices
- request permission
- inspect interfaces and endpoints
- attempt conservative reads from readable endpoints
- display raw payload and parser output

Do not emit business scan events from this page.

## Design

### 1. Screen Structure

Extend [`NfcTestScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt) rather than creating a new screen.

The screen should become a test dashboard with two independent sections:

- the existing NFC test section
- a new `Type-C USB Host` test section

The Type-C section should be visually separate and clearly labeled as exploratory testing for external readers.

This keeps the routing simple and preserves the current login-page test entry.

### 2. New Units and Responsibilities

The feature should be split into three focused units.

#### `TypeCRfidTestViewModel`

A dedicated test-only view model for the Type-C panel.

Responsibilities:

- expose USB probe state to the UI
- request refresh and read actions from the probe manager
- hold the latest device summary, payload, and parsed result
- convert low-level probe results into screen-friendly state

It should not:

- emit production `TagScanned` events
- change production NFC workflow state
- be reused by the sign-in feature yet

#### `UsbHostProbeManager`

A generic probe manager for exploratory USB Host work.

Responsibilities:

- enumerate currently attached USB devices
- observe USB attach and detach changes while the test page is active
- request permission
- read device, interface, and endpoint metadata
- try to open the connection and read from readable endpoints
- return raw bytes and probe diagnostics

It should not:

- assume any vendor protocol
- assume a specific command format
- pretend that a successful probe is the same as a production scan

#### `TypeCRfidTestPanel`

A composable panel rendered inside `NfcTestScreen`.

Responsibilities:

- show current USB Host state
- show device details
- show raw payload
- show parser output
- expose debug actions like refresh, request permission, and attempt read

### 3. UI Sections

The new test panel should include five blocks.

#### A. Connection State

This is the top status area.

Show states such as:

- no USB device detected
- USB device detected
- permission not granted
- permission granted
- probing in progress
- read failed
- raw payload captured

This should be the most prominent section because it answers the first hardware question quickly.

#### B. Device Information

Show the currently selected USB device summary, including at least:

- device name or ID
- vendor ID
- product ID
- device class, subclass, and protocol
- interface count
- endpoint summary

This helps determine whether Android sees the reader as a usable USB device at all.

#### C. Raw Payload

Show the latest raw payload from a generic read attempt in two forms:

- text view
- hexadecimal view

Also show when it was captured, if available.

The first version only needs the latest captured payload, not a full scrolling log.

#### D. Parsed Result

Attempt to run the raw text payload through the existing generic parser.

Show either:

- parsed card ID
- or a clear `未解析出卡号`

This section is informational only. It does not send anything to production workflow logic.

#### E. Action Area

The first version should support these actions:

- `刷新设备`
- `申请权限`
- `开始尝试读取`

A `停止读取` action may be added if the probe manager needs a long-running read operation, but it is not required for the first version.

Manual refresh should remain even after automatic attach and detach observation is added. It serves as a fallback recovery action if the device broadcast path is delayed or unreliable on a given handset.

### 4. State Model

Add a dedicated test-panel state rather than reusing the production `ReaderUiState`.

Recommended shape:

- `UsbProbeUiState.Idle`
- `UsbProbeUiState.NoDevice`
- `UsbProbeUiState.DeviceDetected`
- `UsbProbeUiState.PermissionDenied`
- `UsbProbeUiState.Ready`
- `UsbProbeUiState.Reading`
- `UsbProbeUiState.ReadFailed(message)`

Alongside that state, keep separate fields for:

- `deviceSummary`
- `rawPayloadText`
- `rawPayloadHex`
- `parsedTagId`
- `lastUpdatedAt`

This separation keeps the panel understandable:

- the state answers “what stage are we in?”
- the data fields answer “what did we find?”

### 5. Automatic Device Refresh

The panel should not depend only on a manual refresh button.

Add automatic USB device observation so the test page updates while it remains open:

- when a Type-C device is attached, the panel should automatically re-run the current device refresh path
- when a Type-C device is detached, the panel should automatically re-run the current device refresh path

The simplest implementation model is:

- `UsbHostProbeManager.startObserving(...)`
- `UsbHostProbeManager.stopObserving()`

The observation path may use either:

- callbacks that tell the view model a device attach or detach event happened
- or a small `Flow` of attach and detach events

The important rule is not the transport. The important rule is that the test panel reuses the existing refresh logic rather than maintaining a second state machine for broadcasts.

Keep manual refresh as a user-controlled fallback.

### 6. Generic USB Host Strategy

Without an SDK, the first version should act as a conservative probe.

Recommended sequence:

1. enumerate attached USB devices
2. select the first available reader candidate or show all devices if needed
3. request permission for the selected device
4. inspect interfaces and endpoints
5. attempt a conservative read from readable `IN` endpoints
6. display raw bytes as text and hex
7. pass the text form through `ExternalRfidTagParser`

The first version should avoid speculative writes or vendor-specific commands.

This is a probe, not a protocol implementation.

### 7. Error Handling

Error handling should remain diagnostic and explicit.

Use separate states for:

#### No device

- nothing attached
- Android sees no USB device

#### No permission

- permission required
- permission denied

#### Cannot read

- connection failed
- interface claim failed
- no readable endpoint found
- endpoint read returned nothing or timed out

#### Read but not parsed

- payload exists
- parser did not produce a card ID

This is better than collapsing everything into a generic “失败”, because the panel’s purpose is diagnosis.

### 8. Production Safety

This panel must stay isolated from production business flow.

Explicitly do not:

- call `AppEvent.TagScanned`
- update the NFC sign-in workflow view model
- navigate into order start or end logic

The test page should only display probe information.

This ensures hardware experiments do not create accidental business side effects.

### 9. File Targets

Expected implementation focus:

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginNfcTestButtons.kt`
  - only if the login-page button label or navigation wiring needs a small adjustment
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/` new view model and composables
- `app/src/main/kotlin/com/ytone/longcare/common/utils/` new probe-manager code if it fits the existing utility pattern

Likely new files:

- `TypeCRfidTestViewModel.kt`
- `TypeCRfidTestPanel.kt`
- `UsbHostProbeManager.kt`
- `UsbProbeUiState.kt` or a nearby contracts file

If strings are added, `app/src/main/res/values/strings.xml` will change.

## Acceptance Criteria

1. The existing `NfcTestScreen` includes a new Type-C USB Host test section.
2. The test section can show whether any USB device is detected.
3. The test section can request and reflect USB permission state.
4. The test section shows device, interface, and endpoint summary information.
5. The test section can attempt a generic read and display the latest raw payload as text and hex.
6. The test section attempts to parse the raw payload and displays the parsed card ID or an explicit failure to parse.
7. The test section does not emit production `TagScanned` events.
8. While the test page remains open, attaching or removing the Type-C device automatically refreshes the panel state.
9. The implementation remains generic and does not assume a vendor SDK or private command protocol.

## Verification Strategy

Implementation should verify at least the following.

### Automated

- state mapping tests for `UsbProbeUiState`
- parser display tests for text/hex/result formatting
- UI tests or unit tests for panel copy/state decisions where practical
- tests that attach and detach observation triggers the existing refresh path

### Manual

- open the NFC test page with no Type-C reader attached
- verify `未检测到USB设备`
- attach the Type-C reader and verify the device summary updates
- leave the page open and verify attach updates the panel without tapping refresh
- request USB permission and confirm state changes
- inspect interface and endpoint details
- attempt a read while presenting a card
- verify whether raw payload appears
- verify whether parser result appears
- remove the device and confirm the panel returns to the disconnected/no-device state without tapping refresh

## Risks and Controls

### Risk: The panel is mistaken for production support

If the page emits business events, the test panel becomes a hidden production integration path.

Control:

- keep the panel strictly diagnostic
- never emit `TagScanned` from this feature

### Risk: Generic USB Host read works on no real devices

Without a vendor SDK, the first generic probe may only reveal device metadata and never yield raw card bytes.

Control:

- define success broadly for version one: detection, permission, interface visibility, and raw read attempt visibility are already valuable

### Risk: Overbuilding a debug console

It is easy to turn this into a protocol lab instead of a practical test panel.

Control:

- show only the latest useful values
- keep actions limited to refresh, permission, and read attempt
- avoid export tools, packet history, and write-command experiments in version one

### Risk: Attach and detach observation introduces a second state machine

If broadcast handling mutates panel state separately from refresh logic, the test panel can drift into inconsistent states.

Control:

- use attach and detach observation only to trigger the existing refresh path
- keep one source of truth for panel state mapping in the view model

## Rationale

This design gives the team the fastest safe way to learn whether a generic Type-C USB Host approach is viable for the current reader.

It reuses the existing NFC test page, keeps exploratory device work out of the production sign-in flow, and focuses the first version on the information needed most: can Android see the device, can the app talk to it, does any payload come back, and can that payload be parsed into a usable card ID.
