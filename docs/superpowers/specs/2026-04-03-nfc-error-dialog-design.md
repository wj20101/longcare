# NFC Error Dialog Design

## Context

The NFC workflow currently reports many user-facing errors through short toasts, while also driving the page into `NfcSignInUiState.Error(message)`.

That creates two problems:

- the user can easily miss the toast
- the error handling experience is inconsistent, because the NFC page already uses dialogs for other important decisions such as location activation and end-order confirmation

The current structure already provides the pieces needed for a more reliable experience:

- `NfcSignInUiState.Error(message)` for failure state
- `NfcWorkflowDialogs` as a dedicated dialog host
- a failure page state with a retry action

## Goal

Replace NFC workflow error toasts with dialog-based error prompts so users cannot easily miss important error messages, while preserving the existing failure-state and retry flow.

## Non-Goals

This design does not include:

- changes to successful NFC flows
- changes to the existing location activation dialog
- changes to the existing end-order confirmation dialog
- refactoring all app-wide toasts into dialogs
- backend or repository behavior changes

## Approved Direction

Reuse the existing `NfcSignInUiState.Error(message)` state as the single source of truth for NFC error presentation.

Do not introduce a second parallel error state such as `ShowErrorDialog`. Instead:

- keep `Error(message)` as the failure state
- stop showing the same message via toast
- render an error dialog from `NfcWorkflowDialogs` when `uiState is Error`

This is preferred because it keeps the state model simple and avoids splitting one failure condition across multiple UI channels.

## Design

### 1. Error Presentation Model

When an NFC error occurs:

- set `uiState` to `NfcSignInUiState.Error(message)`
- do not show a toast for that same message
- show a blocking error dialog with the message

When the user dismisses the dialog:

- dismiss only the dialog presentation
- keep the page in the failure state

That means the user still sees the existing failure UI and can use the current retry action.

### 2. Why Keep the Failure State

The NFC failure screen already communicates that the action failed and offers a recovery path through “重新碰一碰”.

If dismissing the dialog also reset the page to `Initial`, the user would lose important context and the failure/retry relationship would become less clear.

So the desired behavior is:

- dialog makes the error hard to miss
- failure state keeps the page visually consistent
- retry remains unchanged

### 3. Dialog Ownership

`NfcWorkflowDialogs` should remain the single dialog host for the NFC workflow screen.

After this change it will handle three dialog categories:

- location activation
- end-order confirmation
- error acknowledgement

This keeps dialog rendering centralized and avoids scattering modal logic across the screen body and effects.

### 4. Toast Replacement Scope

This change applies to NFC workflow errors that are currently surfaced to users via `toastHelper.showShort(...)` in the NFC workflow path, including cases triggered by:

- order-start/order-end API exceptions
- order-start/order-end API failures
- location-related failures raised through `showError(...)`
- end-order execution failures
- NFC scan flow failures that currently bubble through the same error path

The implementation should remove duplicate user-facing toasts for these paths where the page is already transitioned into `NfcSignInUiState.Error(message)`.

### 5. Error Dialog UX

The error dialog should be simple and acknowledgment-based.

Recommended shape:

- title: error/failure oriented, short and clear
- body: exact error message already produced by the workflow
- action: single confirm button such as `我知道了` or `确定`

Dismiss behavior:

- tapping the confirm button closes the dialog
- tapping outside or back should follow the same explicit dismissal rule chosen for this dialog style
- dismissal must not auto-reset the workflow state

### 6. State Handling

The implementation should distinguish between:

- “dialog is visible”
- “workflow is in error state”

Because the error state must remain after the dialog closes, the screen will need a lightweight local or screen-level dialog visibility control layered on top of `uiState is Error`, or an equivalent explicit dismissal mechanism that does not erase the error state itself.

The important rule is:

- dialog dismissal is not the same as error recovery

### 7. Recovery Flow

After the dialog is dismissed:

- the failure UI remains visible
- the bottom action remains the existing retry path
- `resetState()` remains the user-controlled recovery action

No automatic retry or automatic reset should be introduced.

## File Targets

Expected implementation focus:

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`

If string resources are introduced for the dialog title or acknowledgment button, resource files may also change.

## Acceptance Criteria

1. NFC workflow errors no longer rely on short toasts as the primary user-facing prompt.
2. When an NFC error occurs, the user sees a dialog with the error message.
3. After dismissing the dialog, the page remains in the NFC failure state.
4. The existing retry action remains available and unchanged.
5. Existing location activation and end-order confirmation dialogs continue to behave as before.
6. No new duplicate error prompts are introduced for the same failure event.

## Verification Strategy

Implementation should verify at least:

- local Kotlin compilation for touched NFC files
- dialog rendering logic for `NfcSignInUiState.Error`
- real-device or UI-flow verification that the dialog appears on failure and the failure state remains after dismissal

## Risks and Controls

### Risk: Duplicate Prompting

If the old toast path is left in place while the dialog is added, users may get both a toast and a dialog for the same event.

Control:

- remove or suppress toast calls for error paths that already transition into `Error(message)`

### Risk: Dialog Dismissal Resets Too Much

If dismissing the error dialog also resets the workflow to `Initial`, the user loses failure context.

Control:

- separate dialog dismissal from workflow reset
- keep retry as the explicit recovery action

### Risk: Dialog Logic Spreads Across the Screen

Adding error dialog rendering directly in multiple places would make modal behavior harder to maintain.

Control:

- keep `NfcWorkflowDialogs` as the central dialog host

## Rationale

This design solves the actual user problem, which is missed error feedback, without overcomplicating the NFC state machine. It uses the state that already exists, upgrades the presentation channel from toast to dialog, and preserves the failure-and-retry workflow users already understand.
