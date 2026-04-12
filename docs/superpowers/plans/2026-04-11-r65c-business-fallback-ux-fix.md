# R65C Business Fallback UX Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the formal fallback experience on `NfcWorkflowScreen` so the page no longer shows `R65C` wording, does not trigger the soft keyboard, and still captures external HID card swipes through the existing `EXTERNAL_RFID` business boundary.

**Architecture:** Preserve the existing external-reader business boundary (`submitHidCandidate(...)`, strict validation, duplicate suppression, invalid-streak escalation), but replace the workflow-side `TextField` carrier with a keyboardless HID capture surface plus a lightweight session collector. The business page should behave like a reader screen, not a text-entry screen.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `KeyEvent`, `StateFlow`, Hilt ViewModel, JUnit4, MockK, existing NFC workflow and external RFID infrastructure.

---

## File Structure

### New files

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHost.kt`
  - Workflow-local `KeyEvent` filter and mapper for external HID input, derived from the successful raw-HID filtering rules but not tied to the test UI.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHostTest.kt`
  - Unit tests for key filtering, Enter mapping, and ignored non-scan keys.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollector.kt`
  - Lightweight workflow-only session collector that assembles one swipe into one candidate string without any debug-only behavior.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollectorTest.kt`
  - Unit tests for character accumulation, Enter completion, draining, and reset semantics.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidCaptureSurface.kt`
  - Invisible, focusable, non-text-entry capture surface that can receive hardware key events without invoking IME text-entry behavior.

### Deleted files

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt`
  - Remove the `OutlinedTextField`-based HID carrier from the formal workflow.

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
  - Replace text-buffer fallback entry with key-event/session-based fallback handling and completion timing.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
  - Replace the hidden `TextField` carrier with the new keyboardless HID capture surface.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
  - Keep the same `EXTERNAL_RFID` state structure but ensure copy keys still map correctly after text changes.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
  - Update tests to match the user-facing “reader” language instead of `R65C`.
- `app/src/main/res/values/strings.xml`
  - Change fallback copy to generic reader wording.

### Shared files to verify but not redesign

- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
  - Business boundary remains unchanged.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
  - Business validation path remains unchanged.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
  - Top-level workflow screen should not need new user-visible controls.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
  - Reference implementation for `KeyEvent` filtering; do not wire test UI into formal workflow.

### Task 1: Add workflow HID key-event filtering separate from the test UI

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHost.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHostTest.kt`

- [ ] **Step 1: Write the failing host tests for accepted and ignored key events**

```kotlin
package com.ytone.longcare.features.nfc.ui

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65cWorkflowHidKeyCaptureHostTest {

    @Test
    fun `maps digit action down into workflow HID event`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 10L,
        )

        val result = toR65cWorkflowHidCapturedKeyEventIfRelevant(native)

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals("1", result?.displayChar)
        assertEquals(10L, result?.eventTimeMillis)
    }

    @Test
    fun `maps enter into workflow HID newline sentinel`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            unicodeChar = '\n'.code,
            eventTime = 20L,
        )

        val result = toR65cWorkflowHidCapturedKeyEventIfRelevant(native)

        assertEquals("\\n", result?.displayChar)
    }

    @Test
    fun `ignores non action down event`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 30L,
        )

        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(native))
    }

    @Test
    fun `ignores back and volume keys`() {
        val back = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 40L)
        val volume = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 50L)

        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(back))
        assertNull(toR65cWorkflowHidCapturedKeyEventIfRelevant(volume))
    }

    private fun mockKeyEvent(
        action: Int,
        keyCode: Int,
        unicodeChar: Int,
        eventTime: Long,
    ): KeyEvent {
        return mockk {
            every { this@mockk.action } returns action
            every { this@mockk.keyCode } returns keyCode
            every { this@mockk.unicodeChar } returns unicodeChar
            every { this@mockk.eventTime } returns eventTime
        }
    }
}
```

- [ ] **Step 2: Run the new host test class to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.R65cWorkflowHidKeyCaptureHostTest"
```

Expected: FAIL because the workflow key-capture host file does not exist yet.

- [ ] **Step 3: Implement the workflow-local HID key capture host**

```kotlin
package com.ytone.longcare.features.nfc.ui

import android.view.KeyEvent

internal data class R65cWorkflowHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)

internal fun toR65cWorkflowHidCapturedKeyEventIfRelevant(
    keyEvent: KeyEvent,
): R65cWorkflowHidCapturedKeyEvent? {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return null
    if (keyEvent.keyCode in IGNORED_NON_SCAN_KEYS) return null

    val displayChar = when {
        keyEvent.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent.unicodeChar == '\n'.code -> "\\n"
        keyEvent.unicodeChar == 0 -> ""
        else -> keyEvent.unicodeChar.toChar().toString()
    }

    return R65cWorkflowHidCapturedKeyEvent(
        keyCode = keyEvent.keyCode,
        unicodeChar = keyEvent.unicodeChar,
        displayChar = displayChar,
        eventTimeMillis = keyEvent.eventTime,
    )
}

private val IGNORED_NON_SCAN_KEYS = setOf(
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_APP_SWITCH,
)
```

- [ ] **Step 4: Run the host test class to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.R65cWorkflowHidKeyCaptureHostTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHost.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHostTest.kt
git commit -m "feat(nfc): add workflow HID key capture host"
```

### Task 2: Add a lightweight workflow HID session collector

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollector.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollectorTest.kt`

- [ ] **Step 1: Write the failing collector tests**

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.features.nfc.ui.R65cWorkflowHidCapturedKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R65cWorkflowHidSessionCollectorTest {

    @Test
    fun `appends regular characters into one pending session`() {
        val collector = R65cWorkflowHidSessionCollector()

        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))

        assertNull(result)
        assertTrue(collector.hasPendingInput())
    }

    @Test
    fun `enter completes current session`() {
        val collector = R65cWorkflowHidSessionCollector()
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))

        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(66, '\n'.code, "\\n", 2L))

        assertEquals("A", result)
        assertFalse(collector.hasPendingInput())
    }

    @Test
    fun `drainPending returns buffered text and clears it`() {
        val collector = R65cWorkflowHidSessionCollector()
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(29, 'A'.code, "A", 1L))
        collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(30, 'B'.code, "B", 2L))

        val drained = collector.drainPending()

        assertEquals("AB", drained)
        assertFalse(collector.hasPendingInput())
    }

    @Test
    fun `empty display char does not create a session`() {
        val collector = R65cWorkflowHidSessionCollector()

        val result = collector.onKeyEvent(R65cWorkflowHidCapturedKeyEvent(0, 0, "", 1L))

        assertNull(result)
        assertFalse(collector.hasPendingInput())
    }
}
```

- [ ] **Step 2: Run the collector test class to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.R65cWorkflowHidSessionCollectorTest"
```

Expected: FAIL because the collector file does not exist yet.

- [ ] **Step 3: Implement the collector**

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.features.nfc.ui.R65cWorkflowHidCapturedKeyEvent

internal class R65cWorkflowHidSessionCollector {
    private val buffer = StringBuilder()

    fun onKeyEvent(event: R65cWorkflowHidCapturedKeyEvent): String? {
        return if (event.displayChar == "\\n") {
            drainPending()
        } else {
            if (event.displayChar.isNotEmpty()) {
                buffer.append(event.displayChar)
            }
            null
        }
    }

    fun hasPendingInput(): Boolean = buffer.isNotEmpty()

    fun drainPending(): String? {
        if (buffer.isEmpty()) return null
        return buffer.toString().also { buffer.clear() }
    }

    fun reset() {
        buffer.clear()
    }
}
```

- [ ] **Step 4: Run the collector test class to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.R65cWorkflowHidSessionCollectorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollector.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollectorTest.kt
git commit -m "feat(nfc): add workflow HID session collector"
```

### Task 3: Replace the workflow `TextField` carrier with a keyboardless HID capture surface

**Files:**
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidCaptureSurface.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`

- [ ] **Step 1: Change the workflow layout to reference the new keyboardless capture surface**

Replace the existing `R65cWorkflowHidFallbackField(...)` block inside `NfcWorkflowBodyContent(...)` with:

```kotlin
        if (scanMode == ScanMode.EXTERNAL_RFID) {
            R65cWorkflowHidCaptureSurface(
                readerUiState = readerUiState,
                onKeyCaptured = nfcViewModel::onR65cFallbackKeyEvent,
            )
        }
```

Expected compile state before the new composable and ViewModel method exist: FAIL.

- [ ] **Step 2: Replace the current text-payload ViewModel entry with key-event handling**

Update `NfcWorkflowViewModel.kt` with these members:

```kotlin
    private val r65cSessionCollector = R65cWorkflowHidSessionCollector()
    private var r65cCompletionJob: kotlinx.coroutines.Job? = null
```

Add these methods:

```kotlin
    fun onR65cFallbackKeyEvent(event: R65cWorkflowHidCapturedKeyEvent) {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return

        val completedPayload = r65cSessionCollector.onKeyEvent(event)
        if (completedPayload != null) {
            cancelR65cCompletionJob()
            submitR65cFallbackPayload(completedPayload)
            return
        }

        if (!r65cSessionCollector.hasPendingInput()) return

        _readerUiState.value = ReaderUiState.Reading
        cancelR65cCompletionJob()
        r65cCompletionJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400L)
            r65cSessionCollector.drainPending()?.let(::submitR65cFallbackPayload)
        }
    }

    private fun submitR65cFallbackPayload(rawPayload: String) {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return
        _readerUiState.value = ReaderUiState.Ready
        if (rawPayload.isBlank()) return
        externalRfidReaderManager.submitHidCandidate(rawPayload)
    }

    private fun cancelR65cCompletionJob() {
        r65cCompletionJob?.cancel()
        r65cCompletionJob = null
    }
```

Update `onCleared()`:

```kotlin
    override fun onCleared() {
        cancelR65cCompletionJob()
        r65cSessionCollector.reset()
        scanDelegate.clear()
        super.onCleared()
    }
```

- [ ] **Step 3: Add the keyboardless capture surface**

```kotlin
package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfc.vm.ReaderUiState

@Composable
internal fun R65cWorkflowHidCaptureSurface(
    readerUiState: ReaderUiState,
    onKeyCaptured: (R65cWorkflowHidCapturedKeyEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(readerUiState) {
        if (readerUiState == ReaderUiState.Ready || readerUiState == ReaderUiState.Reading) {
            keyboardController?.hide()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                toR65cWorkflowHidCapturedKeyEventIfRelevant(keyEvent.nativeKeyEvent)
                    ?.let(onKeyCaptured)
                false
            }
    )
}
```

- [ ] **Step 4: Delete the old `TextField` HID carrier file**

Delete:

```text
app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt
```

- [ ] **Step 5: Run workflow compilation to verify the new carrier builds**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidCaptureSurface.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHost.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollector.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidKeyCaptureHostTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/R65cWorkflowHidSessionCollectorTest.kt
git rm app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt
git commit -m "refactor(nfc): replace workflow HID text field with key capture surface"
```

### Task 4: Replace technical `R65C` copy with user-facing reader language

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`

- [ ] **Step 1: Update the workflow copy tests to the user-facing reader language**

Replace the copy test names and keep the same key assertions:

```kotlin
    @Test
    fun `external disconnected copy instructs the user to prepare the reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Disconnected,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT, copy.bottomHintKey)
    }

    @Test
    fun `external ready copy instructs the user to scan on the reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Ready,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_HINT, copy.bottomHintKey)
    }
```

- [ ] **Step 2: Update the string resources**

```xml
    <string name="nfc_external_reader_prompt">请准备读卡器</string>
    <string name="nfc_external_reader_disconnected">读卡器未就绪</string>
    <string name="nfc_external_reader_disconnected_hint">请确认读卡器已连接并可刷卡后再继续</string>
    <string name="nfc_external_reader_ready_prompt">请将卡片放在读卡器感应区</string>
    <string name="nfc_external_reader_ready">读卡器已就绪</string>
    <string name="nfc_external_reader_ready_hint">请在读卡器上完成刷卡</string>
```

- [ ] **Step 3: Run the copy test class with a wildcard filter**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "*NfcWorkflowUiCopyTest"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add \
  app/src/main/res/values/strings.xml \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt
git commit -m "fix(nfc): simplify fallback reader copy for users"
```

### Task 5: Run final verification for the UX fix

**Files:**
- Reuse: `docs/superpowers/specs/2026-04-11-r65c-business-fallback-ux-fix-design.md`

- [ ] **Step 1: Run the focused verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.R65cWorkflowHidKeyCaptureHostTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.R65cWorkflowHidSessionCollectorTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.R65cBusinessFallbackFilterTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
./gradlew :app:testDebugUnitTest --tests "*NfcWorkflowUiCopyTest"
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected:

- all five focused unit test commands PASS
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes

- [ ] **Step 2: Manually verify the UX goals on a non-NFC device**

Confirm:

- entering `NfcWorkflowScreen` does not show the soft keyboard
- scanning on the external HID reader does not show the soft keyboard
- copy mentions `读卡器` or equivalent user-facing wording, not `R65C`
- a valid card still flows into the existing business event path

- [ ] **Step 3: Stop only when the carrier has changed but the business boundary has not**

Expected result:

- user-facing UX no longer looks like text entry
- fallback still routes through `ExternalRfidReaderManager.submitHidCandidate(...)`
- existing `EXTERNAL_RFID` business semantics remain intact

## Self-Review

### Spec coverage

- copy no longer shows `R65C`: covered by Task 4
- formal workflow no longer depends on `TextField` as HID carrier: covered by Task 3
- keyboardless HID capture is page-local and not debug UI: covered by Tasks 1, 2, and 3
- business boundary is preserved: covered by Task 3 and validated again in Task 5
- manual validation includes “no soft keyboard on entry or scan”: covered by Task 5

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation notes remain
- Every code-changing step contains concrete code snippets
- Every verification step contains exact commands and expected results

### Type consistency

- `R65cWorkflowHidCapturedKeyEvent`, `R65cWorkflowHidSessionCollector`, and `R65cWorkflowHidCaptureSurface` are defined before later tasks reference them
- `onR65cFallbackKeyEvent(...)` consistently replaces `onR65cFallbackInputChanged(...)`
- wildcard `*NfcWorkflowUiCopyTest` is used consistently because the exact class-name `--tests` filter has already proven unreliable in this project

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-r65c-business-fallback-ux-fix.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
