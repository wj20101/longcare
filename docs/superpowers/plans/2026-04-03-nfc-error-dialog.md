# NFC Error Dialog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace NFC workflow error toasts with dialog-based error prompts while preserving the existing failure-state and retry flow.

**Architecture:** Reuse `NfcSignInUiState.Error(message)` as the single NFC error state, remove duplicate toast presentation from NFC error paths, and render an acknowledgment dialog from the centralized `NfcWorkflowDialogs` host. Keep failure UI and retry behavior unchanged after dialog dismissal.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 dialogs, StateFlow-driven UI state, Hilt ViewModel, Gradle app compilation.

---

## File Structure

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
  Purpose: Own any lightweight dialog-visibility glue if needed so error dialogs can be dismissed without resetting the workflow failure state.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt`
  Purpose: Centralize the new NFC error acknowledgment dialog alongside the existing location activation and end-order confirmation dialogs.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
  Purpose: Keep the NFC error state model aligned with the chosen dialog strategy. Only change this file if an explicit dismissal flag or helper is truly needed.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`
  Purpose: Remove direct toast emission from generic NFC error entry points while still transitioning into `NfcSignInUiState.Error(message)`.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`
  Purpose: Remove duplicate toast emission from order-start/order-end result handling while preserving error-state transitions and the existing special handling for confirmation dialog cases.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`
  Purpose: Remove end-order error toasts that are now replaced by the NFC error dialog path.

- `app/src/main/res/values/strings.xml`
  Purpose: Add dialog title and acknowledgment button text if the NFC error dialog uses dedicated resources.

## Task 1: Add the NFC Error Dialog UI

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add dialog strings**

Add dedicated resources if they do not already exist:

```xml
<string name="nfc_error_dialog_title">提示</string>
<string name="nfc_error_dialog_confirm">我知道了</string>
```

- [ ] **Step 2: Add an NFC error dialog composable**

In `NfcWorkflowDialogs.kt`, add a dedicated error dialog:

```kotlin
@Composable
internal fun NfcErrorDialog(
    message: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onConfirm,
        title = {
            Text(
                text = stringResource(R.string.nfc_error_dialog_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.nfc_error_dialog_confirm),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(12.dp)
    )
}
```

- [ ] **Step 3: Extend `NfcWorkflowDialogs` with an error branch**

Update the `when (uiState)` branch:

```kotlin
is NfcSignInUiState.Error -> {
    NfcErrorDialog(
        message = uiState.message,
        onConfirm = onDismissError
    )
}
```

Also extend `NfcWorkflowDialogs(...)` with a new callback parameter:

```kotlin
onDismissError: () -> Unit
```

- [ ] **Step 4: Run app compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the dialog UI layer**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt \
  app/src/main/res/values/strings.xml
git commit -m "feat(nfc): add error acknowledgment dialog"
```

## Task 2: Make Error Dialog Dismissal Independent from Recovery

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt` (only if needed)

- [ ] **Step 1: Add screen-local error dialog visibility control**

In `NfcWorkflowScreen.kt`, add lightweight dialog visibility state that tracks whether the current error message has already been acknowledged:

```kotlin
var showErrorDialog by remember(uiState) {
    mutableStateOf(uiState is NfcSignInUiState.Error)
}
```

If you need to keep this stable across recompositions while still reacting to new errors, prefer an explicit `LaunchedEffect(uiState)` update instead of overcomplicating the ViewModel contract.

- [ ] **Step 2: Pass the new dismissal callback into `NfcWorkflowDialogs`**

Wire the dialog host call like this:

```kotlin
NfcWorkflowDialogs(
    pendingNfcData = pendingNfcData,
    uiState = uiState,
    onConfirmLocationActivation = nfcViewModel::confirmLocationActivation,
    onCancelLocationActivation = nfcViewModel::cancelLocationActivation,
    onConfirmEndOrder = nfcViewModel::confirmEndOrder,
    onCancelEndOrder = nfcViewModel::cancelEndOrder,
    onDismissError = { showErrorDialog = false }
)
```

If `uiState is NfcSignInUiState.Error` but `showErrorDialog == false`, the dialog host should not render the error dialog again for that same state instance.

- [ ] **Step 3: Keep the failure screen state intact**

Do **not** call:

```kotlin
nfcViewModel.resetState()
```

from dialog dismissal. The failure screen and retry button must remain visible after the user closes the dialog.

- [ ] **Step 4: Run app compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the screen-side dismissal logic**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt
git commit -m "fix(nfc): keep failure state after error dialog dismissal"
```

## Task 3: Remove NFC Error Toasts from Workflow Paths

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`

- [ ] **Step 1: Remove toast emission from generic `showError(...)`**

In `NfcOrderWorkflowDelegate.kt`, change:

```kotlin
fun showError(message: String) {
    toastHelper.showShort(message)
    uiState.value = NfcSignInUiState.Error(message)
}
```

to:

```kotlin
fun showError(message: String) {
    uiState.value = NfcSignInUiState.Error(message)
}
```

- [ ] **Step 2: Remove duplicate toasts from result handlers**

In `NfcOrderWorkflowResultHandlers.kt`, update the error handlers so they only assign error state:

```kotlin
internal fun applyOrderApiException(
    exception: ApiResult.Exception,
    toastHelper: ToastHelper,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    val message = exception.exception.message ?: "网络错误，请检查网络连接"
    uiState.value = NfcSignInUiState.Error(message)
}
```

```kotlin
internal fun applyOrderApiFailure(
    failure: ApiResult.Failure,
    toastHelper: ToastHelper,
    uiState: MutableStateFlow<NfcSignInUiState>
) {
    uiState.value = NfcSignInUiState.Error(failure.message)
}
```

For `applyCheckEndOrderFailure(...)`, preserve the special `code == 3005` confirmation branch exactly as-is, but remove the fallback toast in the non-confirmation branch:

```kotlin
uiState.value = NfcSignInUiState.Error(failure.message)
```

- [ ] **Step 3: Remove duplicate toasts from end-order execution failures**

In `NfcOrderWorkflowEndOrderExecutor.kt`, remove the NFC error toast calls where the code already transitions into `NfcSignInUiState.Error(message)`.

The final shape should preserve:

- existing business branching
- existing `Success` transition
- existing failure message content

but stop calling `toastHelper.showShort(...)` for those error paths.

- [ ] **Step 4: Run app compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the toast-removal cleanup**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt
git commit -m "refactor(nfc): route workflow errors through dialog state"
```

## Task 4: End-to-End Verification

**Files:**
- Modify: none

- [ ] **Step 1: Run NFC-related app compilation one more time**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install the debug app on the connected device**

Run:

```bash
./gradlew :app:installDebug
```

Expected: successful install output for the connected device.

- [ ] **Step 3: Trigger an NFC failure path manually**

Use the app's existing NFC flow to reproduce an error condition, such as:

```text
1. Enter NFC workflow.
2. Force a scan failure, network failure, or location-related failure through the existing flow.
3. Observe whether a dialog appears instead of a transient toast.
```

- [ ] **Step 4: Verify dismissal behavior**

Manual checklist:

```text
1. Confirm the error dialog shows the error message.
2. Dismiss the dialog.
3. Confirm the page still displays the failure state.
4. Confirm the retry action is still available and unchanged.
5. Confirm location activation and end-order confirmation dialogs still behave normally.
```

- [ ] **Step 5: Commit only if final verification requires small polish**

```bash
git status --short
git add <only-follow-up-files-if-needed>
git commit -m "fix(nfc): polish error dialog flow"
```

Skip this step if no additional code changes were needed after verification.

## Self-Review

- **Spec coverage:** The plan covers dialog UI, dismissal semantics, removal of duplicate toasts, preservation of failure/retry state, and verification of existing NFC dialog flows.
- **Placeholder scan:** No `TODO`, `TBD`, or vague “handle appropriately” language remains.
- **Type consistency:** The plan consistently uses `NfcSignInUiState.Error(message)` as the only NFC error state and routes all new modal behavior through `NfcWorkflowDialogs`.
