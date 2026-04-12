# R65C Business Fallback Reader State Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the fallback reader experience so `NfcWorkflowScreen` immediately shows `Ready` when the reader is already connected, and gives users a visible “正在识别” feedback while a card is being recognized.

**Architecture:** Keep the current `EXTERNAL_RFID` business path and validation boundary unchanged, but add an explicit “current readiness” query to the external-reader boundary and use it to seed `ReaderUiState` when the workflow starts. At the UI layer, treat `ReaderUiState.Reading` as a real user-visible state with distinct copy and a lightweight visual indicator.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `StateFlow`, Hilt, JUnit4, existing NFC workflow and external RFID infrastructure.

---

## File Structure

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
  - Add a small readiness query for current reader availability.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
  - Implement the readiness query from the real USB device list.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
  - Seed `ReaderUiState` from the current reader readiness when entering the external-reader path.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
  - Ensure readiness sync happens after the external reader is started and before the page settles into its first visible state.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
  - Add a dedicated copy branch for `ReaderUiState.Reading`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`
  - Show a lightweight visual indicator when the reader is actively recognizing.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt`
  - Extend reader-state tests where needed to keep state reduction expectations explicit.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
  - Add reader-state copy coverage for `Reading`.
- `app/src/main/res/values/strings.xml`
  - Add or update strings for the ready vs reading feedback.

### New files

- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowExternalReaderStateTest.kt`
  - Focused unit tests for reader readiness seeding and state preservation around workflow start.

### Shared files to verify but not redesign

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
  - `ReaderUiState` shape remains unchanged.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
  - Event-driven business flow remains unchanged.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
  - Layout structure should stay stable unless the feedback component forces a small shape change.

## Constraints Locked In

- Do not redesign the `EXTERNAL_RFID` business boundary.
- Do not change `TagScanned(..., ScanSource.EXTERNAL_RFID)` publication rules.
- Do not add a new workflow screen.
- Do not add a manual “start scanning” action.
- Keep `Reading` feedback lightweight and local to the current status card.

### Task 1: Add a current reader readiness query at the external-reader boundary

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`

- [ ] **Step 1: Write the failing boundary contract change**

Update the interface to require current readiness:

```kotlin
interface ExternalRfidReaderManager {
    fun start(activity: Activity)
    fun stop(activity: Activity)
    fun submitHidCandidate(rawPayload: String)
    fun isReaderReady(): Boolean
}
```

- [ ] **Step 2: Run compilation to verify implementations now fail**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: FAIL because `UsbExternalRfidReaderManager` does not yet implement `isReaderReady()`.

- [ ] **Step 3: Implement the readiness query in `UsbExternalRfidReaderManager`**

Add:

```kotlin
    override fun isReaderReady(): Boolean = usbManager.deviceList.isNotEmpty()
```

Keep `start(...)` publishing connection state on entry:

```kotlin
        publishConnectionState(isReaderReady())
```

- [ ] **Step 4: Re-run compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt
git commit -m "feat(nfc): expose current external reader readiness"
```

### Task 2: Seed `ReaderUiState` from current reader readiness when workflow starts

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowExternalReaderStateTest.kt`

- [ ] **Step 1: Add focused tests for initial reader readiness mapping**

Create:

```kotlin
package com.ytone.longcare.features.nfc.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowExternalReaderStateTest {

    @Test
    fun `external reader ready seeds ready state`() {
        val state = initialExternalReaderUiState(isReaderReady = true)

        assertEquals(ReaderUiState.Ready, state)
    }

    @Test
    fun `external reader not ready seeds disconnected state`() {
        val state = initialExternalReaderUiState(isReaderReady = false)

        assertEquals(ReaderUiState.Disconnected, state)
    }
}
```

- [ ] **Step 2: Run the new test class to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcWorkflowExternalReaderStateTest"
```

Expected: FAIL because `initialExternalReaderUiState(...)` does not exist yet.

- [ ] **Step 3: Add a small state helper in `NfcWorkflowViewModel.kt`**

Add:

```kotlin
internal fun initialExternalReaderUiState(isReaderReady: Boolean): ReaderUiState =
    if (isReaderReady) ReaderUiState.Ready else ReaderUiState.Disconnected
```

Use it for the initial state:

```kotlin
    private val _readerUiState = MutableStateFlow(
        if (_scanMode.value == ScanMode.SYSTEM_NFC) {
            ReaderUiState.NotRequired
        } else {
            initialExternalReaderUiState(externalRfidReaderManager.isReaderReady())
        },
    )
```

Add a refresh entry point:

```kotlin
    fun refreshExternalReaderReadyState() {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return
        _readerUiState.value = initialExternalReaderUiState(externalRfidReaderManager.isReaderReady())
    }
```

- [ ] **Step 4: Trigger readiness refresh when the screen starts the external reader**

Update `NfcWorkflowEffects.kt`:

```kotlin
    LaunchedEffect(activity) {
        if (activity != null) {
            nfcViewModel.startActiveScanSource(activity)
            nfcViewModel.refreshExternalReaderReadyState()
        }
    }
```

- [ ] **Step 5: Run the new state test class and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcWorkflowExternalReaderStateTest"
./gradlew :app:compileDebugKotlin
```

Expected: PASS for both commands.

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowExternalReaderStateTest.kt
git commit -m "fix(nfc): seed workflow reader state from current readiness"
```

### Task 3: Add user-visible `Reading` copy and status feedback

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`

- [ ] **Step 1: Add a dedicated copy key for `Reading`**

Extend:

```kotlin
internal enum class NfcWorkflowCopyKey {
    SYSTEM_IDLE_PROMPT,
    SYSTEM_IDLE_STATUS,
    SYSTEM_IDLE_HINT,
    EXTERNAL_DISCONNECTED_PROMPT,
    EXTERNAL_DISCONNECTED_STATUS,
    EXTERNAL_DISCONNECTED_HINT,
    EXTERNAL_READY_PROMPT,
    EXTERNAL_READY_STATUS,
    EXTERNAL_READY_HINT,
    EXTERNAL_READING_PROMPT,
    EXTERNAL_READING_STATUS,
}
```

Handle `Reading` separately:

```kotlin
        ReaderUiState.Reading -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_READING_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_READING_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_READY_HINT,
        )

        ReaderUiState.Ready -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_READY_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_READY_HINT,
        )
```

Map the new keys:

```kotlin
    NfcWorkflowCopyKey.EXTERNAL_READING_PROMPT -> R.string.nfc_external_reader_reading_prompt
    NfcWorkflowCopyKey.EXTERNAL_READING_STATUS -> R.string.nfc_external_reader_reading
```

- [ ] **Step 2: Add the reading strings**

Add:

```xml
    <string name="nfc_external_reader_reading_prompt">正在识别，请保持卡片靠近读卡器</string>
    <string name="nfc_external_reader_reading">正在识别</string>
```

- [ ] **Step 3: Update copy tests**

Add:

```kotlin
    @Test
    fun `external reading copy shows recognizing state`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Reading,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READING_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READING_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_HINT, copy.bottomHintKey)
    }
```

- [ ] **Step 4: Make `SignInContentCard` show a lightweight visual indicator in the idle override region**

Change the signature:

```kotlin
internal fun SignInContentCard(
    signInState: SignInState,
    statusOverrideRes: Int? = null,
    showReadingIndicator: Boolean = false,
)
```

Update the idle branch:

```kotlin
                    if (statusOverrideRes != null) {
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showReadingIndicator) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = stringResource(statusOverrideRes),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
```

- [ ] **Step 5: Pass the reading flag from `NfcWorkflowLayoutSections.kt`**

Update:

```kotlin
        SignInContentCard(
            signInState = signInState,
            statusOverrideRes = statusOverrideRes,
            showReadingIndicator = scanMode == ScanMode.EXTERNAL_RFID && readerUiState == ReaderUiState.Reading,
        )
```

- [ ] **Step 6: Run the copy test class and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*NfcWorkflowUiCopyTest"
./gradlew :app:compileDebugKotlin
```

Expected: PASS for both commands.

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt
git commit -m "feat(nfc): show reader recognition feedback"
```

### Task 4: Run final verification for the reader-state fix

**Files:**
- Reuse: `docs/superpowers/specs/2026-04-11-r65c-business-fallback-reader-state-fix-design.md`

- [ ] **Step 1: Run the focused verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcWorkflowExternalReaderStateTest"
./gradlew :app:testDebugUnitTest --tests "*NfcWorkflowUiCopyTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected:

- all three focused test commands PASS
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes

- [ ] **Step 2: Manually verify the two UX fixes**

Confirm on a non-NFC device:

- entering the page with the reader already connected shows ready state immediately
- scanning shows a short but obvious `正在识别` feedback
- success and failure business flows still behave as before

- [ ] **Step 3: Stop only when reader truth and user feedback both match reality**

Expected result:

- already-connected readers do not appear disconnected on entry
- users can tell the page is actively recognizing a scan
- the external-reader business path remains unchanged

## Self-Review

### Spec coverage

- initial reader-state synchronization is explicit: covered by Tasks 1 and 2
- `Reading` becomes user-visible: covered by Task 3
- business publication rules remain unchanged: preserved by leaving the external-reader boundary intact
- no new workflow screen or heavy loading UI is introduced: preserved by Task 3’s lightweight indicator
- manual verification covers both “already connected” and “recognizing” behavior: covered by Task 4

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation notes remain
- Every code-changing step includes concrete code snippets
- Every verification step includes exact commands and expected results

### Type consistency

- `isReaderReady()` is introduced at the reader boundary before later tasks use it
- `initialExternalReaderUiState(...)` is defined before the tests and effect updates reference it
- `showReadingIndicator` is threaded consistently from layout into `SignInContentCard`
- `EXTERNAL_READING_PROMPT` and `EXTERNAL_READING_STATUS` are introduced together and used consistently

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-r65c-business-fallback-reader-state-fix.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
