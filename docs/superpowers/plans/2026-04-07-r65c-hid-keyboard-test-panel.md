# R65C HID Keyboard Test Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current USB Host probe panel on `NfcTestScreen` with an `R65C`-specific HID keyboard-input test panel that captures raw input, derives a normalized UID, and auto-resets for the next scan.

**Architecture:** Keep the existing `NfcTestScreen` route, but replace the `TypeCRfid*` USB Host test flow with a focused `R65C HID` panel built around a dedicated `OutlinedTextField`. The new flow centers on a `R65CHidInputTestViewModel` that owns a live input buffer, completion detection (`Enter` first, idle timeout second), and the latest completed capture state.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `StateFlow`, Hilt ViewModel, Kotlin coroutines, JUnit4, MockK, Compose UI instrumentation tests.

---

## File Structure

### New files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt`
  - New panel state and capture state for the `R65C` keyboard-input flow.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt`
  - ViewModel that owns the live input buffer, completion logic, and latest capture result.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
  - Compose panel with the focused input field, status text, result area, and action buttons.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContractsTest.kt`
  - Unit tests for default state, display helpers, and failure-state display behavior.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt`
  - Unit tests for focus changes, `Enter` completion, idle-timeout completion, clear/reset, and parse failures.
- `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt`
  - Compose UI tests for panel rendering, action-button wiring, and removal of legacy USB Host controls.

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  - Replace `TypeCRfidTestViewModel` usage with `R65CHidInputTestViewModel`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  - Replace the old `TypeCRfidTestPanel` slot with the new `R65CHidInputTestPanel`.

### Deleted files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManagerSelectionTest.kt`

These deletions are safe once the new screen flow is wired because `rg -n "TypeCRfid|UsbHostProbe"` only finds references inside the retired USB Host test path.

### Shared files to keep

- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
  - Reuse this parser to normalize raw keyboard input into a UID.
- `app/src/test/kotlin/com/ytone/longcare/util/MainDispatcherRule.kt`
  - Reuse this test rule for coroutine-based ViewModel tests.

---

### Task 1: Define the new R65C panel state contracts

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContractsTest.kt`

- [ ] **Step 1: Write the failing contract tests**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class R65CHidInputTestContractsTest {

    @Test
    fun `default state starts waiting for focus with empty displays`() {
        val state = R65CHidPanelState()

        assertEquals(R65CHidCaptureState.WaitingForFocus, state.captureState)
        assertEquals("", state.liveInputBuffer)
        assertEquals("-", state.lastRawInputDisplay)
        assertEquals("未解析出卡号", state.lastNormalizedUidDisplay)
        assertEquals("-", state.lastCompletedAtDisplay)
        assertEquals(0L, state.focusRequestToken)
    }

    @Test
    fun `completed state exposes raw normalized and completed time`() {
        val state = R65CHidPanelState(
            captureState = R65CHidCaptureState.LastCaptureSucceeded,
            lastRawInput = "ab12\r\n",
            lastNormalizedUid = "AB12",
            lastCompletedAt = "12:34:56",
            focusRequestToken = 2L,
        )

        assertEquals("ab12\r\n", state.lastRawInputDisplay)
        assertEquals("AB12", state.lastNormalizedUidDisplay)
        assertEquals("12:34:56", state.lastCompletedAtDisplay)
        assertEquals(2L, state.focusRequestToken)
    }

    @Test
    fun `failed capture exposes fallback uid display`() {
        val state = R65CHidPanelState(
            captureState = R65CHidCaptureState.LastCaptureFailed("未解析出卡号"),
            lastRawInput = "###",
        )

        assertEquals("###", state.lastRawInputDisplay)
        assertEquals("未解析出卡号", state.lastNormalizedUidDisplay)
        assertEquals("未解析出卡号", (state.captureState as R65CHidCaptureState.LastCaptureFailed).reason)
    }
}
```

- [ ] **Step 2: Run the unit test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestContractsTest"
```

Expected: FAIL with a missing class or unresolved reference for `R65CHidPanelState` and `R65CHidCaptureState`.

- [ ] **Step 3: Write the minimal contract implementation**

```kotlin
package com.ytone.longcare.features.nfctest.vm

sealed interface R65CHidCaptureState {
    data object WaitingForFocus : R65CHidCaptureState
    data object ReadyForScan : R65CHidCaptureState
    data object ReceivingInput : R65CHidCaptureState
    data object LastCaptureSucceeded : R65CHidCaptureState
    data class LastCaptureFailed(val reason: String) : R65CHidCaptureState
}

data class R65CHidPanelState(
    val captureState: R65CHidCaptureState = R65CHidCaptureState.WaitingForFocus,
    val liveInputBuffer: String = "",
    val lastRawInput: String? = null,
    val lastNormalizedUid: String? = null,
    val lastCompletedAt: String? = null,
    val focusRequestToken: Long = 0L,
) {
    val lastRawInputDisplay: String = lastRawInput ?: "-"
    val lastNormalizedUidDisplay: String = lastNormalizedUid ?: "未解析出卡号"
    val lastCompletedAtDisplay: String = lastCompletedAt ?: "-"
}
```

- [ ] **Step 4: Run the unit test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestContractsTest"
```

Expected: PASS with `3 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContractsTest.kt
git commit -m "test(nfctest): add R65C HID panel state coverage"
```

### Task 2: Implement the R65C capture ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt`
- Reuse: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
- Reuse: `app/src/test/kotlin/com/ytone/longcare/util/MainDispatcherRule.kt`

- [ ] **Step 1: Write the failing ViewModel tests**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class R65CHidInputTestViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val parser = mockk<ExternalRfidTagParser>()
    private val fixedNow = "12:34:56"

    @Test
    fun `focus gain marks panel ready`() {
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)

        assertEquals(R65CHidCaptureState.ReadyForScan, viewModel.panelState.value.captureState)
    }

    @Test
    fun `enter completes immediately and clears live buffer`() = runTest {
        every { parser.normalize("ab12\r") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12\r")

        assertEquals("", viewModel.panelState.value.liveInputBuffer)
        assertEquals("ab12\r", viewModel.panelState.value.lastRawInput)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals(1L, viewModel.panelState.value.focusRequestToken)
    }

    @Test
    fun `idle timeout completes when enter does not arrive`() = runTest {
        every { parser.normalize("ab12") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12")

        assertEquals(R65CHidCaptureState.ReceivingInput, viewModel.panelState.value.captureState)
        advanceTimeBy(399)
        assertEquals("ab12", viewModel.panelState.value.liveInputBuffer)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(R65CHidCaptureState.LastCaptureSucceeded, viewModel.panelState.value.captureState)
        assertEquals("AB12", viewModel.panelState.value.lastNormalizedUid)
        assertEquals("", viewModel.panelState.value.liveInputBuffer)
    }

    @Test
    fun `normalization failure preserves raw input and marks failure`() = runTest {
        every { parser.normalize("###") } returns null
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("###")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.captureState is R65CHidCaptureState.LastCaptureFailed)
        assertEquals("###", viewModel.panelState.value.lastRawInput)
        assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
    }

    @Test
    fun `clear result removes latest capture and requests focus again`() {
        every { parser.normalize("ab12\r") } returns "AB12"
        val viewModel = R65CHidInputTestViewModel(
            parser = parser,
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFieldFocusChanged(true)
        viewModel.onInputChanged("ab12\r")
        viewModel.clearLastResult()

        assertEquals(null, viewModel.panelState.value.lastRawInput)
        assertEquals(null, viewModel.panelState.value.lastNormalizedUid)
        assertEquals(R65CHidCaptureState.ReadyForScan, viewModel.panelState.value.captureState)
        assertEquals(2L, viewModel.panelState.value.focusRequestToken)
    }
}
```

- [ ] **Step 2: Run the ViewModel test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest"
```

Expected: FAIL with a missing class or unresolved reference for `R65CHidInputTestViewModel`.

- [ ] **Step 3: Write the minimal ViewModel implementation**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class R65CHidInputTestViewModel @Inject constructor(
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var completionDelayMillis: Long = 400L
    private var pendingCompletionJob: Job? = null

    internal constructor(
        parser: ExternalRfidTagParser,
        nowProvider: () -> String,
        completionDelayMillis: Long,
    ) : this(parser) {
        this.nowProvider = nowProvider
        this.completionDelayMillis = completionDelayMillis
    }

    private val _panelState = MutableStateFlow(R65CHidPanelState())
    val panelState: StateFlow<R65CHidPanelState> = _panelState.asStateFlow()

    fun onFieldFocusChanged(isFocused: Boolean) {
        _panelState.update { state ->
            state.copy(
                captureState = when {
                    !isFocused -> R65CHidCaptureState.WaitingForFocus
                    state.liveInputBuffer.isNotEmpty() -> R65CHidCaptureState.ReceivingInput
                    else -> R65CHidCaptureState.ReadyForScan
                },
            )
        }
    }

    fun onInputChanged(newValue: String) {
        if (newValue.contains('\n') || newValue.contains('\r')) {
            completeScan(newValue)
            return
        }

        _panelState.update {
            it.copy(
                liveInputBuffer = newValue,
                captureState = if (newValue.isEmpty()) {
                    R65CHidCaptureState.ReadyForScan
                } else {
                    R65CHidCaptureState.ReceivingInput
                },
            )
        }

        scheduleIdleCompletion()
    }

    fun requestRefocus() {
        pendingCompletionJob?.cancel()
        _panelState.update {
            it.copy(
                captureState = if (it.liveInputBuffer.isEmpty()) {
                    R65CHidCaptureState.ReadyForScan
                } else {
                    R65CHidCaptureState.ReceivingInput
                },
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    fun clearLastResult() {
        pendingCompletionJob?.cancel()
        _panelState.update {
            it.copy(
                captureState = R65CHidCaptureState.ReadyForScan,
                liveInputBuffer = "",
                lastRawInput = null,
                lastNormalizedUid = null,
                lastCompletedAt = null,
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun scheduleIdleCompletion() {
        pendingCompletionJob?.cancel()
        pendingCompletionJob = viewModelScope.launch {
            delay(completionDelayMillis)
            val buffer = panelState.value.liveInputBuffer
            if (buffer.isNotBlank()) {
                completeScan(buffer)
            }
        }
    }

    private fun completeScan(rawInput: String) {
        pendingCompletionJob?.cancel()
        val normalizedUid = parser.normalize(rawInput)

        _panelState.update {
            it.copy(
                captureState = normalizedUid?.let { R65CHidCaptureState.LastCaptureSucceeded }
                    ?: R65CHidCaptureState.LastCaptureFailed("未解析出卡号"),
                liveInputBuffer = "",
                lastRawInput = rawInput,
                lastNormalizedUid = normalizedUid,
                lastCompletedAt = nowProvider(),
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun nowString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onCleared() {
        pendingCompletionJob?.cancel()
        super.onCleared()
    }
}
```

- [ ] **Step 4: Run the ViewModel test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest"
```

Expected: PASS with `5 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModel.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestViewModelTest.kt
git commit -m "feat(nfctest): add R65C HID input view model"
```

### Task 3: Build the R65C Compose panel and panel UI tests

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt`

- [ ] **Step 1: Write the failing Compose UI tests**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R65CHidInputTestPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun refocus_button_invokes_callback() {
        var refocusCount = 0

        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(captureState = R65CHidCaptureState.WaitingForFocus),
                    onInputChanged = {},
                    onFocusChanged = {},
                    onRequestRefocus = { refocusCount++ },
                    onClearResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_refocus_button").performClick()
        assertEquals(1, refocusCount)
    }

    @Test
    fun panel_shows_live_input_and_last_result_separately() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputTestPanel(
                    state = R65CHidPanelState(
                        captureState = R65CHidCaptureState.ReceivingInput,
                        liveInputBuffer = "AB",
                        lastRawInput = "CD\r",
                        lastNormalizedUid = "CD",
                        lastCompletedAt = "12:34:56",
                    ),
                    onInputChanged = {},
                    onFocusChanged = {},
                    onRequestRefocus = {},
                    onClearResult = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_live_input_value").assertExists()
        composeRule.onNodeWithTag("r65c_last_raw_value").assertExists()
        composeRule.onNodeWithTag("r65c_last_uid_value").assertExists()
        composeRule.onNodeWithText("R65C HID 键盘口测试").assertExists()
    }
}
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
```

Expected: FAIL with unresolved reference errors for `R65CHidInputTestPanel`.

- [ ] **Step 3: Write the minimal panel implementation**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState

@Composable
internal fun R65CHidInputTestPanel(
    state: R65CHidPanelState,
    onInputChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onRequestRefocus: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.focusRequestToken) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                text = "状态: ${captureStateLabel(state.captureState)}",
                modifier = Modifier.testTag("r65c_status_label"),
            )
            OutlinedTextField(
                value = state.liveInputBuffer,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .testTag("r65c_input_field"),
                label = { Text("刷卡输入框") },
                singleLine = true,
            )
            Text(
                text = "实时输入: ${state.liveInputBuffer.ifEmpty { "-" }}",
                modifier = Modifier.testTag("r65c_live_input_value"),
            )
            Text(
                text = "原始输入: ${state.lastRawInputDisplay}",
                modifier = Modifier.testTag("r65c_last_raw_value"),
            )
            Text(
                text = "归一化UID: ${state.lastNormalizedUidDisplay}",
                modifier = Modifier.testTag("r65c_last_uid_value"),
            )
            Text(
                text = "完成时间: ${state.lastCompletedAtDisplay}",
                modifier = Modifier.testTag("r65c_last_completed_at"),
            )
            Button(
                onClick = onRequestRefocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("r65c_refocus_button"),
            ) {
                Text("重新聚焦")
            }
            Button(
                onClick = onClearResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("r65c_clear_button"),
            ) {
                Text("清空结果")
            }
        }
    }
}

internal fun captureStateLabel(state: R65CHidCaptureState): String = when (state) {
    R65CHidCaptureState.WaitingForFocus -> "等待聚焦"
    R65CHidCaptureState.ReadyForScan -> "等待刷卡"
    R65CHidCaptureState.ReceivingInput -> "正在输入"
    R65CHidCaptureState.LastCaptureSucceeded -> "捕获成功"
    is R65CHidCaptureState.LastCaptureFailed -> "捕获失败: ${state.reason}"
}
```

- [ ] **Step 4: Run the instrumentation test to verify it passes**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
```

Expected: PASS on a connected emulator or device with `2 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt
git commit -m "feat(nfctest): add R65C HID input panel"
```

### Task 4: Rewire NfcTestScreen and remove the retired USB Host flow

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt`
- Delete: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt`
- Delete: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt`
- Delete: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt`
- Delete: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt`
- Delete: `app/src/test/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManagerSelectionTest.kt`

- [ ] **Step 1: Extend the instrumentation test to assert the new screen wiring**

```kotlin
@Test
fun body_shows_r65c_panel_and_hides_legacy_usb_host_actions() {
    composeRule.setContent {
        LongCareTheme {
            NfcTestBody(
                enabled = true,
                r65cPanelState = R65CHidPanelState(captureState = R65CHidCaptureState.ReadyForScan),
                onR65CInputChanged = {},
                onR65CFocusChanged = {},
                onR65CRequestRefocus = {},
                onR65CClearResult = {},
            )
        }
    }

    composeRule.onNodeWithText("R65C HID 键盘口测试").assertExists()
    composeRule.onNodeWithText("刷新设备").assertDoesNotExist()
    composeRule.onNodeWithText("申请权限").assertDoesNotExist()
    composeRule.onNodeWithText("开始尝试读取").assertDoesNotExist()
}
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
```

Expected: FAIL because `NfcTestBody` still expects the old `typeCPanelState` and legacy callbacks.

- [ ] **Step 3: Rewire the screen and remove the old USB Host files**

```kotlin
// app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt
val r65cViewModel: R65CHidInputTestViewModel = hiltViewModel()
val r65cPanelState by r65cViewModel.panelState.collectAsStateWithLifecycle()

Scaffold(
    topBar = { NfcTestTopBar(onNavigateBack = actions.onNavigateBack) },
    containerColor = Color.Transparent,
) { paddingValues ->
    NfcTestBody(
        enabled = NfcTestConfig.ENABLE_NFC_TEST,
        r65cPanelState = r65cPanelState,
        onR65CInputChanged = r65cViewModel::onInputChanged,
        onR65CFocusChanged = r65cViewModel::onFieldFocusChanged,
        onR65CRequestRefocus = r65cViewModel::requestRefocus,
        onR65CClearResult = r65cViewModel::clearLastResult,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    )
}
```

```kotlin
// app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt
internal fun NfcTestBody(
    enabled: Boolean,
    r65cPanelState: R65CHidPanelState,
    onR65CInputChanged: (String) -> Unit,
    onR65CFocusChanged: (Boolean) -> Unit,
    onR65CRequestRefocus: () -> Unit,
    onR65CClearResult: () -> Unit,
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
        if (enabled) {
            EnabledNfcTestCard()
        } else {
            DisabledNfcTestCard()
        }

        R65CHidInputTestPanel(
            state = r65cPanelState,
            onInputChanged = onR65CInputChanged,
            onFocusChanged = onR65CFocusChanged,
            onRequestRefocus = onR65CRequestRefocus,
            onClearResult = onR65CClearResult,
        )
    }
}
```

```bash
# Remove the retired USB Host-only files once the new screen wiring compiles.
git rm \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManagerSelectionTest.kt
```

- [ ] **Step 4: Run the full verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestContractsTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModelTest"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.nfctest.ui.R65CHidInputTestPanelTest
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
rg -n "TypeCRfid|UsbHostProbe" app/src/main app/src/test
```

Expected:

- both unit-test commands PASS
- the instrumentation test PASSes on a connected emulator or device
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes
- `rg` returns no matches

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
        app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt \
        app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanelTest.kt
git commit -m "refactor(nfctest): replace usb host probe with R65C HID test flow"
```

## Self-Review

### Spec coverage

- `R65C`-specific panel replaces the old USB Host flow: covered in Task 4.
- dedicated input-box strategy: covered in Task 3 and Task 4.
- `Enter`-first plus idle-timeout fallback: covered in Task 2.
- latest raw input plus normalized UID: covered in Task 1 and Task 2.
- auto-clear and refocus for next scan: covered in Task 2 and Task 3.
- limited action set (`重新聚焦`, `清空结果`): covered in Task 3.
- test coverage for contracts, ViewModel logic, and Compose UI behavior: covered in Tasks 1, 2, and 3.
- removal of retired USB Host artifacts: covered in Task 4.

### Placeholder scan

- No `TODO`, `TBD`, “similar to Task N”, or undefined helper names remain.
- All code steps include concrete Kotlin or shell snippets.

### Type consistency

- Main state types are consistently named `R65CHidPanelState` and `R65CHidCaptureState`.
- ViewModel methods are consistently named:
  - `onFieldFocusChanged`
  - `onInputChanged`
  - `requestRefocus`
  - `clearLastResult`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-07-r65c-hid-keyboard-test-panel.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
