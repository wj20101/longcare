# R65C Raw HID Output Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a raw HID validation panel that captures `R65C` key events, compares them with text-field output, and derives candidate UID interpretations without touching the formal business workflow.

**Architecture:** Keep the existing `R65C HID 键盘口测试` panel as a smoke test and add a second `R65C 原始 HID 输出验证` panel in `NfcTestScreen`. The new panel uses a dedicated validation ViewModel plus a candidate-mapping helper so raw key events, assembled characters, and text-field output can be inspected side by side.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose `onPreviewKeyEvent`, `StateFlow`, Hilt ViewModel, JUnit4, MockK, Compose UI instrumentation tests.

---

## File Structure

### New files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
  - Raw-validation state, event models, completion reason, and candidate-value models.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapper.kt`
  - Pure helper that turns one completed capture into several candidate interpretations.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
  - Session handling, event collection, completion logic, and candidate generation.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
  - Validation panel UI with raw-key capture surface, event log, and candidate display.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`
  - Unit tests for state defaults and display helpers.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapperTest.kt`
  - Unit tests for candidate generation rules.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
  - Unit tests for session capture, completion, timeout, and event assembly.
- `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`
  - Compose UI tests for the new validation panel.

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  - Add the new validation ViewModel and collect its state.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  - Extend `NfcTestBody(...)` to render the raw validation panel alongside the smoke-test panel.

### Shared files to reuse

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
  - Keep this as the smoke-test panel.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidInputTestContracts.kt`
  - Do not repurpose this file; the new raw-validation state should be separate.
- `app/src/test/kotlin/com/ytone/longcare/util/MainDispatcherRule.kt`
  - Reuse for coroutine-based ViewModel tests.

---

### Task 1: Define raw-validation contracts

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`

- [ ] **Step 1: Write the failing contracts test**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class R65CHidRawValidationContractsTest {

    @Test
    fun `default raw validation state starts empty and waiting for focus`() {
        val state = R65CHidRawValidationState()

        assertEquals(R65CHidRawCaptureState.WaitingForFocus, state.captureState)
        assertEquals("", state.textFieldValue)
        assertEquals("", state.currentSessionAssembledChars)
        assertEquals("-", state.lastSessionTextFieldValueDisplay)
        assertEquals("-", state.lastSessionAssembledCharsDisplay)
        assertEquals("-", state.lastCompletedReasonDisplay)
        assertEquals("-", state.lastCompletedAtDisplay)
        assertEquals(0, state.lastSessionEvents.size)
        assertEquals(0, state.candidateValues.size)
        assertEquals(0L, state.focusRequestToken)
    }

    @Test
    fun `completed state exposes displays and candidate summary`() {
        val state = R65CHidRawValidationState(
            captureState = R65CHidRawCaptureState.Completed,
            lastSessionTextFieldValue = "901948不EA8想0想",
            lastSessionAssembledChars = "901948EA80",
            lastCompletedReason = R65CHidCompletionReason.EnterKey,
            candidateValues = listOf(
                R65CHidCandidateValue(
                    kind = R65CHidCandidateKind.HexFiltered,
                    value = "901948EA80",
                    note = "looks like 10 hex",
                )
            ),
            lastCompletedAt = "21:52:05",
        )

        assertEquals("901948不EA8想0想", state.lastSessionTextFieldValueDisplay)
        assertEquals("901948EA80", state.lastSessionAssembledCharsDisplay)
        assertEquals("Enter结束", state.lastCompletedReasonDisplay)
        assertEquals("21:52:05", state.lastCompletedAtDisplay)
        assertEquals("901948EA80", state.candidateValues.single().value)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationContractsTest"
```

Expected: FAIL with unresolved references for `R65CHidRawValidationState`, `R65CHidRawCaptureState`, and the candidate model types.

- [ ] **Step 3: Write the minimal contracts implementation**

```kotlin
package com.ytone.longcare.features.nfctest.vm

sealed interface R65CHidRawCaptureState {
    data object WaitingForFocus : R65CHidRawCaptureState
    data object ReadyForScan : R65CHidRawCaptureState
    data object ReceivingKeys : R65CHidRawCaptureState
    data object Completed : R65CHidRawCaptureState
    data class CaptureError(val message: String) : R65CHidRawCaptureState
}

enum class R65CHidCompletionReason {
    EnterKey,
    IdleTimeout,
}

enum class R65CHidCandidateKind {
    RawText,
    RawAssembled,
    HexFiltered,
    DecimalToHex,
    ReversedFourByteHex,
    Classification,
}

data class R65CHidCapturedKeyEvent(
    val keyCode: Int,
    val unicodeChar: Int,
    val action: Int,
    val displayChar: String,
    val eventTimeMillis: Long,
)

data class R65CHidCandidateValue(
    val kind: R65CHidCandidateKind,
    val value: String,
    val note: String,
)

data class R65CHidRawValidationState(
    val captureState: R65CHidRawCaptureState = R65CHidRawCaptureState.WaitingForFocus,
    val textFieldValue: String = "",
    val currentSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val currentSessionAssembledChars: String = "",
    val lastSessionTextFieldValue: String? = null,
    val lastSessionAssembledChars: String? = null,
    val lastSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val lastCompletedReason: R65CHidCompletionReason? = null,
    val candidateValues: List<R65CHidCandidateValue> = emptyList(),
    val lastCompletedAt: String? = null,
    val focusRequestToken: Long = 0L,
) {
    val lastSessionTextFieldValueDisplay: String
        get() = lastSessionTextFieldValue ?: "-"

    val lastSessionAssembledCharsDisplay: String
        get() = lastSessionAssembledChars ?: "-"

    val lastCompletedReasonDisplay: String
        get() = when (lastCompletedReason) {
            R65CHidCompletionReason.EnterKey -> "Enter结束"
            R65CHidCompletionReason.IdleTimeout -> "超时结束"
            null -> "-"
        }

    val lastCompletedAtDisplay: String
        get() = lastCompletedAt ?: "-"
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationContractsTest"
```

Expected: PASS with `2 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt
git commit -m "test(nfctest): add raw HID validation contracts"
```

### Task 2: Add candidate interpretation mapping

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapper.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapperTest.kt`

- [ ] **Step 1: Write the failing candidate-mapper test**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R65CHidRawCandidateMapperTest {

    @Test
    fun `hex-like assembled chars produce raw and filtered candidates`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "04:26:FA:FA:05:1F:91",
            assembledChars = "0426FAFA051F91",
        )

        assertEquals(R65CHidCandidateKind.RawAssembled, candidates[0].kind)
        assertEquals("0426FAFA051F91", candidates[0].value)
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.HexFiltered && it.value == "0426FAFA051F91" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "looks like 14 hex" })
    }

    @Test
    fun `numeric assembled chars produce decimal and reversed four byte candidates`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "4210697732",
            assembledChars = "4210697732",
        )

        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.DecimalToHex && it.value == "FAFA2604" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.ReversedFourByteHex && it.value == "0426FAFA" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "numeric only" })
    }

    @Test
    fun `non ascii input is classified as polluted`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "901948不EA8想0想",
            assembledChars = "901948不EA8想0想",
        )

        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "contains non-ASCII" })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawCandidateMapperTest"
```

Expected: FAIL with unresolved reference for `buildR65CHidCandidateValues`.

- [ ] **Step 3: Write the minimal candidate-mapper implementation**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import java.util.Locale

internal fun buildR65CHidCandidateValues(
    textFieldValue: String,
    assembledChars: String,
): List<R65CHidCandidateValue> {
    val candidates = mutableListOf<R65CHidCandidateValue>()

    val rawAssembled = assembledChars.trim()
    val rawText = textFieldValue.trim()

    if (rawAssembled.isNotEmpty()) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.RawAssembled,
            value = rawAssembled,
            note = "event-assembled",
        )
    }

    if (rawText.isNotEmpty()) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.RawText,
            value = rawText,
            note = "text-field visible value",
        )
    }

    val hexOnly = rawAssembled
        .uppercase(Locale.ROOT)
        .replace(Regex("[^0-9A-F]"), "")

    if (hexOnly.isNotEmpty()) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.HexFiltered,
            value = hexOnly,
            note = when (hexOnly.length) {
                8 -> "looks like 8 hex"
                14 -> "looks like 14 hex"
                else -> "hex-only filtered"
            },
        )
    }

    if (rawAssembled.all(Char::isDigit)) {
        rawAssembled.toLongOrNull()?.let { decimalValue ->
            val decimalHex = decimalValue.toString(16).uppercase(Locale.ROOT)
            candidates += R65CHidCandidateValue(
                kind = R65CHidCandidateKind.DecimalToHex,
                value = decimalHex,
                note = "decimal-to-hex",
            )

            if (decimalHex.length <= 8) {
                val padded = decimalHex.padStart(8, '0')
                val bytes = padded.chunked(2)
                val reversed = bytes.reversed().joinToString("")
                candidates += R65CHidCandidateValue(
                    kind = R65CHidCandidateKind.ReversedFourByteHex,
                    value = reversed,
                    note = "reversed 4-byte hex candidate",
                )
            }
        }

        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = rawAssembled,
            note = "numeric only",
        )
    } else if (rawAssembled.any { it.code > 127 }) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = rawAssembled,
            note = "contains non-ASCII",
        )
    } else if (hexOnly.length == 8) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = rawAssembled,
            note = "looks like 8 hex",
        )
    } else if (hexOnly.length == 14) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = rawAssembled,
            note = "looks like 14 hex",
        )
    } else {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = rawAssembled,
            note = "invalid for business UID",
        )
    }

    return candidates
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawCandidateMapperTest"
```

Expected: PASS with `3 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapper.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapperTest.kt
git commit -m "feat(nfctest): add raw HID candidate mapper"
```

### Task 3: Implement raw-validation ViewModel

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
- Reuse: `app/src/test/kotlin/com/ytone/longcare/util/MainDispatcherRule.kt`

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.util.MainDispatcherRule
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
class R65CHidRawValidationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val fixedNow = "21:52:05"

    @Test
    fun `key events are assembled into one session`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 29, unicodeChar = 'A'.code, action = 0, displayChar = "A", eventTimeMillis = 1L),
        )
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 30, unicodeChar = 'B'.code, action = 0, displayChar = "B", eventTimeMillis = 2L),
        )

        assertEquals(R65CHidRawCaptureState.ReceivingKeys, viewModel.panelState.value.captureState)
        assertEquals("AB", viewModel.panelState.value.currentSessionAssembledChars)
        assertEquals(2, viewModel.panelState.value.currentSessionEvents.size)
    }

    @Test
    fun `enter completes session immediately`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("0426FAFA051F91")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 66, unicodeChar = '\n'.code, action = 0, displayChar = "\\n", eventTimeMillis = 3L),
        )

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals("0426FAFA051F91", viewModel.panelState.value.lastSessionTextFieldValue)
        assertEquals(R65CHidCompletionReason.EnterKey, viewModel.panelState.value.lastCompletedReason)
        assertEquals(fixedNow, viewModel.panelState.value.lastCompletedAt)
    }

    @Test
    fun `idle timeout completes session`() = runTest {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onFocusChanged(true)
        viewModel.onTextFieldValueChanged("4210697732")
        viewModel.onCapturedKey(
            R65CHidCapturedKeyEvent(keyCode = 8, unicodeChar = '4'.code, action = 0, displayChar = "4", eventTimeMillis = 1L),
        )

        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(R65CHidRawCaptureState.Completed, viewModel.panelState.value.captureState)
        assertEquals(R65CHidCompletionReason.IdleTimeout, viewModel.panelState.value.lastCompletedReason)
        assertTrue(viewModel.panelState.value.candidateValues.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: FAIL with unresolved reference for `R65CHidRawValidationViewModel`.

- [ ] **Step 3: Write the minimal ViewModel implementation**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class R65CHidRawValidationViewModel @Inject constructor() : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var completionDelayMillis: Long = 400L
    private var completionJob: Job? = null

    internal constructor(
        nowProvider: () -> String,
        completionDelayMillis: Long,
    ) : this() {
        this.nowProvider = nowProvider
        this.completionDelayMillis = completionDelayMillis
    }

    private val _panelState = MutableStateFlow(R65CHidRawValidationState())
    val panelState: StateFlow<R65CHidRawValidationState> = _panelState.asStateFlow()

    fun onFocusChanged(isFocused: Boolean) {
        _panelState.update {
            it.copy(
                captureState = if (isFocused) {
                    if (it.currentSessionEvents.isEmpty()) R65CHidRawCaptureState.ReadyForScan else R65CHidRawCaptureState.ReceivingKeys
                } else {
                    R65CHidRawCaptureState.WaitingForFocus
                },
            )
        }
        if (!isFocused) cancelCompletionJob()
    }

    fun onTextFieldValueChanged(newValue: String) {
        _panelState.update { it.copy(textFieldValue = newValue) }
    }

    fun onCapturedKey(event: R65CHidCapturedKeyEvent) {
        cancelCompletionJob()
        val updatedEvents = _panelState.value.currentSessionEvents + event
        val updatedChars = _panelState.value.currentSessionAssembledChars + event.displayChar.takeUnless { it == "\\n" } .orEmpty()

        _panelState.update {
            it.copy(
                captureState = R65CHidRawCaptureState.ReceivingKeys,
                currentSessionEvents = updatedEvents,
                currentSessionAssembledChars = updatedChars,
            )
        }

        if (event.displayChar == "\\n") {
            completeSession(R65CHidCompletionReason.EnterKey)
            return
        }

        completionJob = viewModelScope.launch {
            delay(completionDelayMillis)
            if (_panelState.value.currentSessionEvents.isNotEmpty()) {
                completeSession(R65CHidCompletionReason.IdleTimeout)
            }
        }
    }

    fun requestRefocus() {
        _panelState.update { it.copy(focusRequestToken = it.focusRequestToken + 1) }
    }

    fun clearLastSession() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                captureState = R65CHidRawCaptureState.ReadyForScan,
                textFieldValue = "",
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = null,
                lastSessionAssembledChars = null,
                lastSessionEvents = emptyList(),
                lastCompletedReason = null,
                candidateValues = emptyList(),
                lastCompletedAt = null,
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun completeSession(reason: R65CHidCompletionReason) {
        cancelCompletionJob()
        val completedState = _panelState.value
        _panelState.update {
            it.copy(
                captureState = R65CHidRawCaptureState.Completed,
                lastSessionTextFieldValue = completedState.textFieldValue,
                lastSessionAssembledChars = completedState.currentSessionAssembledChars,
                lastSessionEvents = completedState.currentSessionEvents,
                lastCompletedReason = reason,
                candidateValues = buildR65CHidCandidateValues(
                    textFieldValue = completedState.textFieldValue,
                    assembledChars = completedState.currentSessionAssembledChars,
                ),
                lastCompletedAt = nowProvider(),
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun cancelCompletionJob() {
        completionJob?.cancel()
        completionJob = null
    }

    private fun nowString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: PASS with `3 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt
git commit -m "feat(nfctest): add raw HID validation view model"
```

### Task 4: Build the raw-validation panel UI

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`

- [ ] **Step 1: Write the failing panel test**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateKind
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateValue
import com.ytone.longcare.features.nfctest.vm.R65CHidCompletionReason
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R65CHidRawValidationPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun panel_shows_text_events_and_candidates() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidRawValidationPanel(
                    state = R65CHidRawValidationState(
                        captureState = R65CHidRawCaptureState.Completed,
                        textFieldValue = "901948不EA8想0想",
                        lastSessionTextFieldValue = "901948不EA8想0想",
                        lastSessionAssembledChars = "901948EA80",
                        lastCompletedReason = R65CHidCompletionReason.EnterKey,
                        candidateValues = listOf(
                            R65CHidCandidateValue(
                                kind = R65CHidCandidateKind.HexFiltered,
                                value = "901948EA80",
                                note = "looks like 10 hex",
                            )
                        ),
                        lastCompletedAt = "21:52:05",
                    ),
                    onTextFieldValueChanged = {},
                    onCapturedKey = {},
                    onFocusChanged = {},
                    onRequestRefocus = {},
                    onClearSession = {},
                )
            }
        }

        composeRule.onNodeWithTag("r65c_raw_text_value").assertTextEquals("901948不EA8想0想")
        composeRule.onNodeWithTag("r65c_raw_assembled_value").assertTextEquals("901948EA80")
        composeRule.onNodeWithTag("r65c_candidate_0_value").assertTextEquals("901948EA80")
        composeRule.onNodeWithTag("r65c_completed_reason").assertTextEquals("Enter结束")
        composeRule.onNodeWithTag("r65c_completed_at").assertTextEquals("21:52:05")
    }
}
```

- [ ] **Step 2: Run the panel test compile to verify it fails**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL with unresolved reference for `R65CHidRawValidationPanel`.

- [ ] **Step 3: Write the minimal panel implementation**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState

@Composable
internal fun R65CHidRawValidationPanel(
    state: R65CHidRawValidationState,
    onTextFieldValueChanged: (String) -> Unit,
    onCapturedKey: (R65CHidCapturedKeyEvent) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onRequestRefocus: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.focusRequestToken) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("R65C 原始 HID 输出验证", style = MaterialTheme.typography.titleMedium)
            Text(rawValidationStateLabel(state.captureState), modifier = Modifier.testTag("r65c_raw_status"))

            OutlinedTextField(
                value = state.textFieldValue,
                onValueChange = onTextFieldValueChanged,
                label = { Text("原始验证输入框") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent {
                        onCapturedKey(
                            R65CHidCapturedKeyEvent(
                                keyCode = it.key.keyCode,
                                unicodeChar = it.utf16CodePoint,
                                action = it.type.ordinal,
                                displayChar = if (it.utf16CodePoint == 10) "\\n" else it.utf16CodePoint.toChar().toString(),
                                eventTimeMillis = System.currentTimeMillis(),
                            )
                        )
                        false
                    }
                    .testTag("r65c_raw_input_field"),
            )

            Text("文本层结果")
            Text(state.lastSessionTextFieldValueDisplay, modifier = Modifier.testTag("r65c_raw_text_value"))

            Text("事件拼装结果")
            Text(state.lastSessionAssembledCharsDisplay, modifier = Modifier.testTag("r65c_raw_assembled_value"))

            Text("完成原因")
            Text(state.lastCompletedReasonDisplay, modifier = Modifier.testTag("r65c_completed_reason"))

            Text("完成时间")
            Text(state.lastCompletedAtDisplay, modifier = Modifier.testTag("r65c_completed_at"))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.candidateValues) { index, candidate ->
                    Text(candidate.kind.name, modifier = Modifier.testTag("r65c_candidate_${index}_kind"))
                    Text(candidate.value, modifier = Modifier.testTag("r65c_candidate_${index}_value"))
                }
            }

            Button(onClick = onRequestRefocus, modifier = Modifier.testTag("r65c_raw_refocus_button")) {
                Text("重新聚焦")
            }
            Button(onClick = onClearSession, modifier = Modifier.testTag("r65c_raw_clear_button")) {
                Text("清空会话")
            }
        }
    }
}
```

- [ ] **Step 4: Run panel test compile to verify it passes**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS with the new test and panel compiling successfully.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt
git commit -m "feat(nfctest): add raw HID validation panel"
```

### Task 5: Wire the validation panel into NfcTestScreen

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`

- [ ] **Step 1: Write a failing androidTest compile expectation by extending the panel test to assert the screen shows both panels**

```kotlin
@Test
fun body_shows_smoke_panel_and_raw_validation_panel() {
    composeRule.setContent {
        LongCareTheme {
            NfcTestBody(
                enabled = true,
                r65cPanelState = R65CHidPanelState(captureState = R65CHidCaptureState.ReadyForScan),
                onR65CInputChanged = {},
                onR65CFocusChanged = {},
                onR65CRequestRefocus = {},
                onR65CClearResult = {},
                rawValidationState = R65CHidRawValidationState(captureState = R65CHidRawCaptureState.ReadyForScan),
                onRawTextFieldValueChanged = {},
                onRawCapturedKey = {},
                onRawFocusChanged = {},
                onRawRequestRefocus = {},
                onRawClearSession = {},
            )
        }
    }

    composeRule.onNodeWithText("R65C HID 键盘口测试").assertExists()
    composeRule.onNodeWithText("R65C 原始 HID 输出验证").assertExists()
}
```

- [ ] **Step 2: Run androidTest compile to verify it fails**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because `NfcTestBody(...)` does not yet accept the raw-validation state and callbacks.

- [ ] **Step 3: Update screen wiring**

```kotlin
// app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt
val rawValidationViewModel: R65CHidRawValidationViewModel = hiltViewModel()
val rawValidationState by rawValidationViewModel.panelState.collectAsStateWithLifecycle()

NfcTestBody(
    enabled = NfcTestConfig.ENABLE_NFC_TEST,
    r65cPanelState = r65cPanelState,
    onR65CInputChanged = r65cViewModel::onInputChanged,
    onR65CFocusChanged = r65cViewModel::onFieldFocusChanged,
    onR65CRequestRefocus = r65cViewModel::requestRefocus,
    onR65CClearResult = r65cViewModel::clearLastResult,
    rawValidationState = rawValidationState,
    onRawTextFieldValueChanged = rawValidationViewModel::onTextFieldValueChanged,
    onRawCapturedKey = rawValidationViewModel::onCapturedKey,
    onRawFocusChanged = rawValidationViewModel::onFocusChanged,
    onRawRequestRefocus = rawValidationViewModel::requestRefocus,
    onRawClearSession = rawValidationViewModel::clearLastSession,
    modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
)
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
    rawValidationState: R65CHidRawValidationState,
    onRawTextFieldValueChanged: (String) -> Unit,
    onRawCapturedKey: (R65CHidCapturedKeyEvent) -> Unit,
    onRawFocusChanged: (Boolean) -> Unit,
    onRawRequestRefocus: () -> Unit,
    onRawClearSession: () -> Unit,
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

        R65CHidRawValidationPanel(
            state = rawValidationState,
            onTextFieldValueChanged = onRawTextFieldValueChanged,
            onCapturedKey = onRawCapturedKey,
            onFocusChanged = onRawFocusChanged,
            onRequestRefocus = onRawRequestRefocus,
            onClearSession = onRawClearSession,
        )
    }
}
```

- [ ] **Step 4: Run the key verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationContractsTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawCandidateMapperTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected:

- all three unit-test commands PASS
- `compileDebugAndroidTestKotlin` PASSes
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt
git commit -m "feat(nfctest): wire raw HID validation panel into test screen"
```

## Self-Review

### Spec coverage

- keep validation work inside the existing test surface: covered by Task 5
- keep the old R65C panel as smoke test: covered by Task 5
- add a dedicated raw validation panel: covered by Task 4
- collect both text-field and key-event layers: covered by Task 3 and Task 4
- expose key logs and candidate values: covered by Task 1, Task 2, and Task 4
- distinguish device-output vs IME pollution by comparison: covered by the state model and candidate-generation tasks
- avoid production integration for now: no task touches `NfcWorkflowScreen` or `AppEvent.TagScanned`

### Placeholder scan

- No `TODO`, `TBD`, or vague “implement later” instructions remain
- Each code step includes concrete file paths, code, and commands

### Type consistency

- State types used consistently:
  - `R65CHidRawValidationState`
  - `R65CHidRawCaptureState`
  - `R65CHidCapturedKeyEvent`
  - `R65CHidCandidateValue`
  - `R65CHidCompletionReason`
- ViewModel method names used consistently:
  - `onFocusChanged`
  - `onTextFieldValueChanged`
  - `onCapturedKey`
  - `requestRefocus`
  - `clearLastSession`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-09-r65c-raw-hid-output-validation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
