# Type-C RFID Fallback Design

## Context

The NFC workflow in LongCare currently assumes that the phone can read tags through the built-in NFC stack.

That assumption no longer holds for all devices. Some phones used in the field do not support NFC, but the business flow still requires a tag scan before starting or ending an order. The new requirement is to support a Type-C RFID reader as a fallback scan source when the phone itself does not support NFC.

The existing workflow already has a strong business path after a tag is read:

- the page enters through [`NfcWorkflowScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt)
- the screen starts scan observation in [`NfcWorkflowEffects`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt)
- scan handling routes through [`NfcScanWorkflowDelegate`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt)
- the workflow only needs a normalized `tagId` before it can continue with location validation, start-order logic, or end-order logic

That makes this feature a scan-source expansion, not a second sign-in workflow.

## Goal

Allow the NFC workflow to continue on phones without built-in NFC by automatically switching to a Type-C RFID reader mode, while reusing the existing order-start and order-end business flow once a `tagId` is available.

## Non-Goals

This design does not include:

- support for Bluetooth RFID readers
- support for manual card ID entry
- a user-facing mode switch between built-in NFC and external readers
- a device-specific protocol implementation for one concrete reader vendor
- changes to backend start-order or end-order APIs
- changes to the existing success and failure business rules after a valid `tagId` is obtained

## Approved Direction

Use a unified scan event model.

The workflow should support two scan sources:

- built-in system NFC on supported phones
- Type-C external RFID readers on phones without NFC

Both sources must be converted into the same business-level scan event before they reach the order workflow.

The order workflow should consume normalized `tagId` values and should not care whether the tag came from Android NFC intents or an external Type-C device.

This is preferred because it keeps the business flow single-path, reduces duplication, and makes future reader changes cheaper.

## Design

### 1. Scan Mode Selection

Introduce a workflow-level scan mode concept with two values:

- `SYSTEM_NFC`
- `EXTERNAL_RFID`

When the NFC workflow screen opens:

- if the device supports built-in NFC, the screen enters `SYSTEM_NFC`
- if the device does not support built-in NFC, the screen automatically enters `EXTERNAL_RFID`

The screen must not ask the user to choose a mode. The fallback should happen automatically.

In `SYSTEM_NFC` mode, the current behavior remains in place.

In `EXTERNAL_RFID` mode, the screen should not show the old error message that the device does not support NFC. Instead, it should transition into an external-reader waiting state.

### 2. Unified Scan Event Layer

The current flow is centered on `AppEvent.NfcIntentReceived(intent)`. That is too transport-specific for a system that now has more than one scan source.

Add business-level scan events to the app event layer so that the workflow can listen for normalized scan information rather than raw Android NFC intents.

Recommended event types:

- `TagScanned(tagId: String, source: ScanSource)`
- `ReaderConnectionChanged(connected: Boolean)`
- `ReaderError(message: String)`

`ScanSource` should distinguish where the scan came from, at least:

- `SYSTEM_NFC`
- `EXTERNAL_RFID`

The event layer should follow this rule:

- raw NFC intents are an input mechanism
- raw USB or reader bytes are an input mechanism
- `TagScanned` is the business event the workflow consumes

This keeps the order workflow clean and makes it possible to support more reader types later without rewriting the business flow.

### 3. External Reader Integration Layer

Add a dedicated manager for the Type-C reader, for example `ExternalRfidReaderManager`.

Its responsibilities are limited to device integration:

- detect Type-C reader insertion and removal
- request and track USB permission when required
- open and close the reader connection
- listen for raw scan data
- normalize the reader payload into a `tagId`
- publish connection and scan events to the shared event layer

Its responsibilities do not include:

- order start logic
- order end logic
- location binding
- business validation against the current order
- page navigation

The reader integration layer should be paired with a small parsing boundary such as `ExternalRfidTagParser` or `ReaderProtocolAdapter` so that device protocol details stay isolated.

That boundary allows the team to support one real device later without changing the workflow screen or the order workflow logic.

### 4. Workflow Consumption Path

[`NfcScanWorkflowDelegate`](app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt) should stop depending only on `NfcIntentReceived`.

Instead, it should observe the unified event stream and react to `TagScanned`.

The handling path should be:

1. receive `TagScanned(tagId, source)`
2. ignore the event if the workflow is already in `Success`
3. request location through the existing location callback
4. route to the existing start-order or end-order path through `executeSignInModeAction`
5. preserve existing confirmation, error, and retry behavior

The business flow after `tagId` acquisition should remain the same for both sources.

### 5. Screen State Model

Do not overload `NfcSignInUiState` with reader connection states.

`NfcSignInUiState` currently describes business progress and business failure. External reader connection state is a device-level concern, not a business-level sign-in result.

Add a separate reader-facing state model for the screen, for example `ReaderUiState`, with states such as:

- `NotRequired`
- `Disconnected`
- `Ready`
- `Reading`
- `DeviceError(message)`

Recommended interpretation:

- `NotRequired` means the screen is in built-in NFC mode
- `Disconnected` means external reader mode is active and no reader is available
- `Ready` means the reader is connected and waiting for a card
- `Reading` is optional and can be used if the real device benefits from a transient progress state
- `DeviceError(message)` means the device integration failed before a valid `tagId` reached the business flow

This separation keeps the screen honest:

- device connection problems remain device state
- order failures remain business state

### 6. Screen UX in External Reader Mode

Reuse the existing page structure in [`NfcWorkflowScreen`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt), [`NfcWorkflowLayoutSections`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt), and [`NfcWorkflowContentComponents`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt).

Do not create a separate screen.

When the workflow enters `EXTERNAL_RFID` mode, adjust the visible guidance while keeping the same layout shell.

Recommended user-facing behavior:

#### External reader disconnected

- top guidance: explain that the phone does not support NFC and requires a Type-C reader
- status text: `未连接读卡器`
- helper text: instruct the user to insert the reader and retry automatically

#### External reader ready

- top guidance: explain that the reader is connected and waiting for a card
- status text: `已连接，等待刷卡`
- helper text: explain that the workflow will continue automatically after a successful scan

#### Business success or failure after a scan

Once a valid `tagId` reaches the business flow, keep using the current success and failure presentation model.

That means:

- device waiting states are shown before a valid scan
- business success and business failure are shown after a valid scan triggers the existing workflow

### 7. Error Handling Rules

Handle errors in three layers.

#### Device integration errors

Examples:

- USB permission denied
- reader connection failed
- reader disconnected during use
- raw data cannot be decoded

These should update `ReaderUiState` and should not immediately move the screen into `NfcSignInUiState.Error`.

The reason is simple: the order workflow has not started yet.

#### Scan data errors

Examples:

- empty `tagId`
- malformed card ID
- duplicate data bursts from a noisy reader

These should be filtered at the reader integration or parser boundary before they enter the workflow.

Recommended controls:

- drop empty or invalid tag IDs
- debounce repeated scans within a short interval
- only emit `TagScanned` after the value passes normalization

#### Business workflow errors

Examples:

- location lookup failed
- location binding failed
- start-order API failed
- end-order API failed
- business validation failed after scan

These should continue to use the current NFC workflow failure model:

- `NfcSignInUiState.Error`
- the existing dialog host in [`NfcWorkflowDialogs`](app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt)
- the existing retry path

### 8. Compatibility Strategy

Because the concrete Type-C reader protocol is not yet fixed, the implementation must not hard-code the whole feature around one integration style.

Use an adapter boundary so the app can later support one of several transport types without redesigning the workflow:

- vendor SDK callback style
- HID keyboard-like input
- USB serial or other direct USB transport

The compatibility rule is:

- workflow layer depends on normalized events and reader state
- reader manager depends on device connection and lifecycle
- protocol adapter depends on device-specific data format

This design keeps future reader changes isolated.

### 9. Lifecycle and Ownership

`NfcWorkflowEffects` should own startup and shutdown of the active scan source.

Recommended behavior:

- in `SYSTEM_NFC` mode, enable and disable the current `NfcManager` as the screen enters and leaves
- in `EXTERNAL_RFID` mode, start and stop the external reader manager as the screen enters and leaves
- do not keep both scan sources active at the same time on the same screen session

This avoids duplicate events, unclear ownership, and inconsistent cleanup.

### 10. Minimal File Targets

Expected implementation focus:

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpers.kt`
- `core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt`

Likely new files:

- an external reader manager under `app/src/main/kotlin/com/ytone/longcare/common/utils/` or a nearby device package
- a parser or protocol adapter for raw reader data

If UI copy changes are extracted into resources, string resource files will also change.

## Acceptance Criteria

1. On phones with built-in NFC support, the existing NFC workflow still works without regression.
2. On phones without built-in NFC support, the screen automatically enters external reader mode instead of failing immediately.
3. In external reader mode, the screen distinguishes at least these states:
   - `未连接读卡器`
   - `已连接，等待刷卡`
4. Both built-in NFC scans and external reader scans are normalized into the same business-level scan event.
5. After a normalized `tagId` is produced, start-order and end-order logic reuse the existing workflow path.
6. Device connection problems do not masquerade as business sign-in failures.
7. Existing business failure handling remains in place after a valid scan reaches the workflow.
8. The design leaves room for device-specific protocol adaptation without redesigning the page or the order workflow.

## Verification Strategy

Implementation should verify at least the following.

### Built-in NFC regression

- supported NFC phones still enter `SYSTEM_NFC`
- NFC intents still lead to successful start-order and end-order flows
- current success, failure, and retry behavior still work

### External reader UI states

- phones without NFC enter `EXTERNAL_RFID`
- no reader connected shows the disconnected state
- reader connected shows the ready state
- disconnecting the reader returns the UI to the disconnected state

### Unified scan behavior

- both scan sources emit the same business-level scan event shape
- a valid `tagId` from either source triggers the same location and order workflow path
- repeated device bursts do not trigger duplicate business requests

### Failure scenarios

- USB permission denial
- device insertion followed by removal
- unreadable device payload
- location request failure after scan
- start-order or end-order API failure after scan
- leaving the page during external reader mode cleans up the active reader connection

## Risks and Controls

### Risk: Device state and business state become entangled

If reader connection state is forced into `NfcSignInUiState`, the page will mislabel transport failures as order failures.

Control:

- keep `ReaderUiState` separate from `NfcSignInUiState`

### Risk: External mode breaks supported NFC devices

If the fallback logic changes the existing NFC path too aggressively, supported phones may regress.

Control:

- preserve `SYSTEM_NFC` as the default path on supported devices
- only activate external reader mode when built-in NFC is unavailable

### Risk: Future device changes require a redesign

If the first implementation is tightly coupled to one concrete protocol, later hardware changes will be expensive.

Control:

- isolate transport and parsing behind a reader manager and protocol adapter boundary

### Risk: Duplicate or noisy scans trigger repeated business actions

Some readers may emit the same card data multiple times.

Control:

- normalize and debounce scan data before emitting `TagScanned`

## Rationale

This design adds a new scan source without creating a second workflow.

The business path in LongCare is already centered on `tagId` after a scan. By converting both built-in NFC and Type-C reader scans into one business event model, the app can support new hardware while keeping the order workflow simple, stable, and easier to maintain.
