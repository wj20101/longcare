# NfcTest Screen NFC/HID Mode Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `NfcTestScreen` capability-driven so NFC devices show only the NFC test flow, non-NFC devices show only the HID keyboard test flow, the HID path never shows the soft keyboard, the final recognized UID can be copied, and the screen is locked to portrait.

**Architecture:** Keep the existing `NfcTestScreen` route and test-entry gate, but split the screen into NFC mode and HID mode at the UI entry layer using `NfcUtils.isNfcSupported(context)`. Reuse the proven workflow-style HID interaction model by adding a hidden focusable capture surface that feeds `R65CHidInputTestViewModel`, while converting the visible HID panel into a read-only status/results surface with a copy action.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `StateFlow`, Hilt ViewModel, Compose UI tests, JUnit4, MockK, Android key-event handling

---

## File Responsibility Map

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt`
  Purpose: keep owning HID parsing/completion state, but gain a key-event driven input API so the UI no longer has to behave like a text form.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt`
  Purpose: remain the home of the HID panel state and, after raw validation cleanup, own the shared captured-key event model too.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHost.kt`
  Purpose: filter host `KeyEvent`s into relevant HID-captured key events for the test page.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureSurface.kt`
  Purpose: hidden focusable capture surface that requests focus, hides the soft keyboard, and forwards HID key events to the view model.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
  Purpose: become a read-only operator-facing panel that shows status/results and offers refocus, clear, and copy actions.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  Purpose: lock portrait orientation, choose NFC vs HID mode, wire clipboard/toast copy behavior, attach the HID capture surface, and remove raw validation wiring.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  Purpose: render mode-specific content only and stop carrying raw-validation parameters.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHostTest.kt`
  Purpose: verify host key filtering for HID mode.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt`
  Purpose: verify HID key-event driven completion and no-soft-keyboard-compatible input flow.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContentModeTest.kt`
  Purpose: verify NFC mode and HID mode render the right sections and hide the wrong ones.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt`
  Purpose: verify the panel becomes read-only and the copy action is correctly enabled/disabled.

- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`
- Delete after migration: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`
  Purpose: remove dead raw-validation code once the test page no longer depends on it.

## Task 1: Add HID Key-Event Capture to the Existing Input ViewModel

**Files:**
- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHost.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHostTest.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt`

- [ ] **Step 1: Extend the unit tests to describe key-event driven HID capture**

Add these tests to `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt`:

```kotlin
    @Test
    fun `captured key sequence completes immediately on enter`() {
        every { parser.normalize("AB12\n") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 30,
                unicodeChar = 'B'.code,
                action = 0,
                displayChar = "B",
                eventTimeMillis = 2L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 8,
                unicodeChar = '1'.code,
                action = 0,
                displayChar = "1",
                eventTimeMillis = 3L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 9,
                unicodeChar = '2'.code,
                action = 0,
                displayChar = "2",
                eventTimeMillis = 4L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 66,
                unicodeChar = '\n'.code,
                action = 0,
                displayChar = "\\n",
                eventTimeMillis = 5L,
            ),
        )

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12\n", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `captured key sequence completes after idle timeout without enter`() = runTest {
        every { parser.normalize("AB12") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 30,
                unicodeChar = 'B'.code,
                action = 0,
                displayChar = "B",
                eventTimeMillis = 2L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 8,
                unicodeChar = '1'.code,
                action = 0,
                displayChar = "1",
                eventTimeMillis = 3L,
            ),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 9,
                unicodeChar = '2'.code,
                action = 0,
                displayChar = "2",
                eventTimeMillis = 4L,
            ),
        )

        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
    }
```

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHostTest.kt` with:

```kotlin
package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65CHidInputCaptureHostTest {

    @Test
    fun `host captures digit key on action down`() {
        val native = mockKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_1,
            unicodeChar = '1'.code,
            eventTime = 20L,
        )

        val result = toR65CHidCapturedKeyEventIfRelevant(native)

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals('1'.code, result?.unicodeChar)
        assertEquals("1", result?.displayChar)
        assertEquals(20L, result?.eventTimeMillis)
    }

    @Test
    fun `host ignores action up back and volume keys`() {
        val keyUp = mockKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1, '1'.code, 30L)
        val back = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 40L)
        val volume = mockKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 50L)

        assertNull(toR65CHidCapturedKeyEventIfRelevant(keyUp))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(back))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(volume))
    }

    private fun mockKeyEvent(
        action: Int,
        keyCode: Int,
        unicodeChar: Int,
        eventTime: Long,
    ): KeyEvent = mockk {
        every { this@mockk.action } returns action
        every { this@mockk.keyCode } returns keyCode
        every { this@mockk.unicodeChar } returns unicodeChar
        every { this@mockk.eventTime } returns eventTime
    }
}
```

- [ ] **Step 2: Run the new unit tests and confirm they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest" \
  --tests "com.ytone.longcare.features.nfctest.ui.R65CHidInputCaptureHostTest"
```

Expected:
- FAIL because `onCapturedKey(...)` and `toR65CHidCapturedKeyEventIfRelevant(...)` do not exist yet

- [ ] **Step 3: Implement the minimal key-event capture API while keeping the old text API temporarily compatible**

Create `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHost.kt`:

```kotlin
package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent

fun toR65CHidCapturedKeyEventIfRelevant(
    keyEvent: KeyEvent,
): R65CHidCapturedKeyEvent? {
    if (keyEvent.action != KeyEvent.ACTION_DOWN) {
        return null
    }

    if (keyEvent.keyCode in IGNORED_NON_SCAN_KEYS) {
        return null
    }

    return R65CHidCapturedKeyEvent(
        keyCode = keyEvent.keyCode,
        unicodeChar = keyEvent.unicodeChar,
        action = keyEvent.action,
        displayChar = keyEvent.toDisplayChar(),
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

private fun KeyEvent.toDisplayChar(): String {
    if (keyCode == KeyEvent.KEYCODE_ENTER || unicodeChar == '\n'.code) {
        return "\\n"
    }

    if (unicodeChar == 0) {
        return ""
    }

    return unicodeChar.toChar().toString()
}
```

Update `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt` with this new API:

```kotlin
    fun onCapturedKey(event: R65CHidCapturedKeyEvent) {
        val nextValue = when {
            event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                event.unicodeChar == '\n'.code -> panelState.value.liveInputBuffer + "\n"

            event.unicodeChar == 0 -> panelState.value.liveInputBuffer
            else -> panelState.value.liveInputBuffer + event.unicodeChar.toChar()
        }

        onInputChanged(nextValue)
    }
```

Keep `onInputChanged(...)` in place for this task so the worker can migrate the UI in the next task without breaking compilation midway.

- [ ] **Step 4: Run the unit tests again and confirm they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest" \
  --tests "com.ytone.longcare.features.nfctest.ui.R65CHidInputCaptureHostTest"
```

Expected:
- PASS with `0 failed`

- [ ] **Step 5: Commit the capture API foundation**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHost.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHostTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt
git commit -m "feat(nfctest): add hid key capture foundation"
```

## Task 2: Split the Screen into NFC Mode and HID Mode

**Files:**
- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureSurface.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContentModeTest.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt`

- [ ] **Step 1: Write the failing UI tests for mode rendering and the read-only HID panel**

Create `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContentModeTest.kt`:

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcTestScreenContentModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun supports_nfc_shows_only_nfc_content() {
        composeRule.setContent {
            LongCareTheme {
                NfcTestBody(
                    enabled = true,
                    supportsNfc = true,
                    r65cPanelState = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReadyForScan,
                    ),
                    onR65CRequestRefocus = {},
                    onR65CClearResult = {},
                    onR65CCopyResult = {},
                )
            }
        }

        composeRule.onNodeWithText("碰一碰ID读取").assertExists()
        composeRule.onAllNodesWithText("R65C HID 键盘口测试").assertCountEquals(0)
        composeRule.onAllNodesWithText("原始 HID 输出验证").assertCountEquals(0)
    }

    @Test
    fun no_nfc_support_shows_only_hid_content() {
        composeRule.setContent {
            LongCareTheme {
                NfcTestBody(
                    enabled = true,
                    supportsNfc = false,
                    r65cPanelState = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReadyForScan,
                    ),
                    onR65CRequestRefocus = {},
                    onR65CClearResult = {},
                    onR65CCopyResult = {},
                )
            }
        }

        composeRule.onNodeWithText("R65C HID 键盘口测试").assertExists()
        composeRule.onAllNodesWithText("碰一碰ID读取").assertCountEquals(0)
        composeRule.onAllNodesWithText("原始 HID 输出验证").assertCountEquals(0)
    }
}
```

Update `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt` with this new test:

```kotlin
    @Test
    fun panel_is_read_only_and_copy_button_depends_on_uid_presence() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(
                        captureState = R65CHidCaptureState.LastCaptureSucceeded,
                        liveInputBuffer = "AB12",
                        lastRawInput = "AB12\n",
                        lastNormalizedUid = "AB12",
                        lastCompletedAt = "12:34:56",
                    ),
                    onRequestRefocus = {},
                    onClearResult = {},
                    onCopyResult = {},
                )
            }
        }

        composeRule.onAllNodesWithTag("r65c_input_field").assertCountEquals(0)
        composeRule.onNodeWithTag("r65c_live_input_value").assertTextEquals("AB12")
        composeRule.onNodeWithTag("r65c_copy_button").assertIsEnabled()
    }
```

- [ ] **Step 2: Run the UI tests and confirm they fail**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.NfcTestScreenContentModeTest,com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
```

Expected:
- FAIL because `NfcTestBody` does not support `supportsNfc`
- FAIL because `R65CHidInputTestPanel` still exposes `r65c_input_field`
- FAIL because `onR65CCopyResult`/`onCopyResult` are not wired yet

- [ ] **Step 3: Implement portrait locking, capability split, hidden capture, and copy wiring**

Create `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureSurface.kt`:

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent

@Composable
internal fun R65CHidInputCaptureSurface(
    enabled: Boolean,
    focusRequestToken: Long,
    onFocusChanged: (Boolean) -> Unit,
    onKeyCaptured: (R65CHidCapturedKeyEvent) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(enabled, focusRequestToken) {
        if (enabled) {
            keyboardController?.hide()
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .size(1.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                toR65CHidCapturedKeyEventIfRelevant(keyEvent.nativeKeyEvent)?.let(onKeyCaptured)
                false
            },
    )
}
```

Update `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt` so the panel becomes read-only:

```kotlin
@Composable
internal fun R65CHidInputTestPanel(
    state: R65CHidPanelState,
    onRequestRefocus: () -> Unit,
    onClearResult: () -> Unit,
    onCopyResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "R65C HID 键盘口测试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = captureStateLabel(state.captureState),
                modifier = Modifier.testTag("r65c_status_label"),
            )

            Text("当前会话输入:")
            Text(
                text = state.liveInputBuffer.ifBlank { "-" },
                modifier = Modifier.testTag("r65c_live_input_value"),
            )

            Text("最近原始输入:")
            Text(
                text = state.lastRawInputDisplay,
                modifier = Modifier.testTag("r65c_last_raw_value"),
            )

            Text("最近标准化UID:")
            Text(
                text = state.lastNormalizedUidDisplay,
                modifier = Modifier.testTag("r65c_last_uid_value"),
            )

            Text("完成时间:")
            Text(
                text = state.lastCompletedAtDisplay,
                modifier = Modifier.testTag("r65c_last_completed_at"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRequestRefocus,
                    modifier = Modifier.testTag("r65c_refocus_button"),
                ) { Text("重新聚焦") }

                Button(
                    onClick = onClearResult,
                    modifier = Modifier.testTag("r65c_clear_button"),
                ) { Text("清空结果") }

                Button(
                    onClick = onCopyResult,
                    enabled = !state.lastNormalizedUid.isNullOrBlank(),
                    modifier = Modifier.testTag("r65c_copy_button"),
                ) { Text("复制结果") }
            }
        }
    }
}
```

Update `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`:

```kotlin
@Composable
internal fun NfcTestBody(
    enabled: Boolean,
    supportsNfc: Boolean,
    r65cPanelState: R65CHidPanelState,
    onR65CRequestRefocus: () -> Unit,
    onR65CClearResult: () -> Unit,
    onR65CCopyResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            !enabled -> DisabledNfcTestCard()
            supportsNfc -> EnabledNfcTestCard()
            else -> R65CHidInputTestPanel(
                state = r65cPanelState,
                onRequestRefocus = onR65CRequestRefocus,
                onClearResult = onR65CClearResult,
                onCopyResult = onR65CCopyResult,
            )
        }
    }
}
```

Update `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`:

```kotlin
@Composable
fun NfcTestScreen(
    actions: NfcTestActions,
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val nfcTestViewModel: NfcTestViewModel = hiltViewModel()
    val r65cViewModel: R65CHidInputTestViewModel = hiltViewModel()
    val r65cPanelState by r65cViewModel.panelState.collectAsStateWithLifecycle()
    val testEntryEnabled by NfcTestEntrySession.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val supportsNfc = remember(context) { NfcUtils.isNfcSupported(context) }

    val nfcTestHelper = if (testEntryEnabled && supportsNfc) {
        nfcTestViewModel.getHelper()
    } else {
        null
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    if (testEntryEnabled && supportsNfc && nfcTestHelper != null) {
        BindNfcTestLifecycle(
            enabled = true,
            context = context,
            lifecycleOwner = lifecycleOwner,
            onEnable = nfcTestViewModel::enableNfcTest,
            onDisable = nfcTestViewModel::disableNfcTest,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (testEntryEnabled && !supportsNfc) {
            R65CHidInputCaptureSurface(
                enabled = true,
                focusRequestToken = r65cPanelState.focusRequestToken,
                onFocusChanged = r65cViewModel::onFieldFocusChanged,
                onKeyCaptured = r65cViewModel::onCapturedKey,
            )
        }

        Scaffold(
            topBar = { NfcTestTopBar(onNavigateBack = actions.onNavigateBack) },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            NfcTestBody(
                enabled = testEntryEnabled,
                supportsNfc = supportsNfc,
                r65cPanelState = r65cPanelState,
                onR65CRequestRefocus = r65cViewModel::requestRefocus,
                onR65CClearResult = r65cViewModel::clearLastResult,
                onR65CCopyResult = {
                    r65cPanelState.lastNormalizedUid
                        ?.takeIf(String::isNotBlank)
                        ?.let { uid ->
                            clipboardManager.setText(AnnotatedString(uid))
                            context.showShortToast("已复制卡号")
                        }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    if (testEntryEnabled && supportsNfc && nfcTestHelper != null) {
        nfcTestHelper.NfcTagDialog()
    }
}
```

- [ ] **Step 4: Run the UI tests again and confirm they pass**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.NfcTestScreenContentModeTest,com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
```

Then run:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest
```

Expected:
- connected UI tests PASS
- Kotlin compile passes
- AndroidTest APK assembly passes

- [ ] **Step 5: Commit the mode split and portrait/copy integration**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureSurface.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContentModeTest.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt
git commit -m "feat(nfctest): split nfc and hid test modes"
```

## Task 3: Delete Raw Validation Dead Code and Move the Shared Event Model

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`
- Delete: `/Users/wajie/StudioProjects/longcare/app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`

- [ ] **Step 1: Verify that raw validation is no longer referenced from the test page**

Run:

```bash
rg -n "R65CHidRawValidation|rawValidation|R65CHidRawCaptureHost" app/src/main/kotlin/com/ytone/longcare/features/nfctest
```

Expected:
- only raw-validation implementation files themselves remain
- `NfcTestScreen.kt` and `NfcTestScreenContent.kt` are absent from the results

- [ ] **Step 2: Move the shared captured-key event into the input contracts and delete the raw-validation files**

Update `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt` to include:

```kotlin
data class R65CHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val action: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)
```

Then delete:

```text
app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt
app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt
app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt
app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt
app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt
app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt
app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt
app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt
```

- [ ] **Step 3: Run targeted regressions and confirm the cleanup did not break the remaining test surface**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestContractsTest" \
  --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest" \
  --tests "com.ytone.longcare.features.nfctest.ui.R65CHidInputCaptureHostTest"
```

Then run:

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest
```

Expected:
- all targeted unit tests PASS
- debug Kotlin compilation passes
- AndroidTest APK assembly passes

- [ ] **Step 4: Commit the raw-validation removal**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHost.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureSurface.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContractsTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputCaptureHostTest.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContentModeTest.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt
git add -u
git commit -m "refactor(nfctest): remove raw hid validation flow"
```

## Self-Review

- Spec coverage:
  - portrait lock is covered in Task 2
  - NFC/HID capability split is covered in Task 2
  - no-soft-keyboard HID capture is covered in Tasks 1 and 2
  - copy final normalized UID is covered in Task 2
  - raw validation removal and dead-code deletion are covered in Task 3

- Placeholder scan:
  - no `TODO`, `TBD`, or “similar to” placeholders remain
  - every task includes exact file paths and exact commands

- Type consistency:
  - `R65CHidCapturedKeyEvent` is first reused, then moved into `R65CHidInputTestContracts.kt` during cleanup
  - `NfcTestBody` ends with `supportsNfc`, `onR65CRequestRefocus`, `onR65CClearResult`, and `onR65CCopyResult`
  - `R65CHidInputTestPanel` ends with `onRequestRefocus`, `onClearResult`, and `onCopyResult`
