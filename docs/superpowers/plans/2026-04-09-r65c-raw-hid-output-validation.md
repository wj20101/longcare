# R65C Raw HID Output Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the current raw HID validation feature so host-level key capture becomes the authoritative source, while the text field remains only a comparison layer.

**Architecture:** Keep the current `R65C HID 键盘口测试` panel as a smoke test and keep the `R65C 原始 HID 输出验证` panel in `NfcTestScreen`, but move raw key capture responsibility out of the panel and up to the test-screen host. The host will forward relevant `KeyEvent` values only while validation is armed, and the validation ViewModel will own session state, candidate generation, and lifecycle.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `KeyEvent`, `StateFlow`, Hilt ViewModel, JUnit4, MockK, Compose UI instrumentation tests.

## Related Docs

- index: [`../README.md`](../README.md)
- design: [`../specs/2026-04-09-r65c-raw-hid-output-validation-design.md`](../specs/2026-04-09-r65c-raw-hid-output-validation-design.md)
- acceptance checklist: [`../specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md`](../specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md)

## Recommended Reading Order

1. Read the design doc for intent, constraints, and architecture.
2. Execute this implementation plan task by task.
3. Validate the result with the acceptance checklist after implementation.

---

## File Structure

### New files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
  - Host-level event filter/adapter that converts Android `KeyEvent` into `R65CHidCapturedKeyEvent` and decides when the raw validation flow is allowed to consume events.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`
  - Unit tests for host-level filtering and conversion rules.

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
  - Replace old focus-centric capture states with listener-centric states.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`
  - Update tests for the new state model and display behavior.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
  - Remove panel-level capture assumptions and adopt `Idle / Armed / Capturing` listener semantics.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
  - Update tests for arm/stop behavior and host-driven event capture.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
  - Simplify the panel into a display/control surface; remove in-panel raw event authority.
- `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`
  - Update tests from focus/textfield semantics to listening controls + display semantics.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  - Install the host-level raw key capture adapter at the screen level.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  - Keep rendering both panels, but update the raw panel callback shape if needed.

### Shared files to keep

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
  - Remains the smoke-test panel.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapper.kt`
  - Candidate generation remains isolated and reusable.
- `app/src/test/kotlin/com/ytone/longcare/util/MainDispatcherRule.kt`
  - Reuse for coroutine-based ViewModel tests.

---

### Task 1: Refactor raw-validation contracts to host-listener semantics

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt`

- [ ] **Step 1: Write the failing contracts test for the new state model**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class R65CHidRawValidationContractsTest {

    @Test
    fun `default raw validation state starts idle and not listening`() {
        val state = R65CHidRawValidationState()

        assertEquals(R65CHidRawCaptureState.Idle, state.captureState)
        assertEquals(false, state.isListening)
        assertEquals("", state.textFieldValue)
        assertEquals("", state.currentSessionAssembledChars)
        assertEquals("-", state.lastSessionTextFieldValueDisplay)
        assertEquals("-", state.lastSessionAssembledCharsDisplay)
        assertEquals("-", state.lastCompletedReasonDisplay)
        assertEquals("-", state.lastCompletedAtDisplay)
        assertEquals(0, state.lastSessionEvents.size)
        assertEquals(0, state.candidateValues.size)
    }

    @Test
    fun `completed state still exposes displays and candidate summary`() {
        val state = R65CHidRawValidationState(
            captureState = R65CHidRawCaptureState.Completed,
            isListening = false,
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

Expected: FAIL because the current contract still uses the old focus-centric defaults.

- [ ] **Step 3: Write the minimal contract refactor**

```kotlin
sealed interface R65CHidRawCaptureState {
    data object Idle : R65CHidRawCaptureState
    data object Armed : R65CHidRawCaptureState
    data object Capturing : R65CHidRawCaptureState
    data object Completed : R65CHidRawCaptureState
    data class CaptureError(val message: String) : R65CHidRawCaptureState
}

data class R65CHidRawValidationState(
    val captureState: R65CHidRawCaptureState = R65CHidRawCaptureState.Idle,
    val isListening: Boolean = false,
    val textFieldValue: String = "",
    val currentSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val currentSessionAssembledChars: String = "",
    val lastSessionTextFieldValue: String? = null,
    val lastSessionAssembledChars: String? = null,
    val lastSessionEvents: List<R65CHidCapturedKeyEvent> = emptyList(),
    val lastCompletedReason: R65CHidCompletionReason? = null,
    val candidateValues: List<R65CHidCandidateValue> = emptyList(),
    val lastCompletedAt: String? = null,
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

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContractsTest.kt
git commit -m "refactor(nfctest): align raw validation contracts with host capture"
```

### Task 2: Add host-level raw key capture adapter

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`

- [ ] **Step 1: Write the failing host test**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65CHidRawCaptureHostTest {

    @Test
    fun `host ignores non listening state`() {
        val native = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1)

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = false,
            currentState = R65CHidRawCaptureState.Idle,
            keyEvent = native,
        )

        assertNull(result)
    }

    @Test
    fun `host captures digit key while armed`() {
        val native = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1)

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = true,
            currentState = R65CHidRawCaptureState.Armed,
            keyEvent = native,
        )

        assertEquals(KeyEvent.KEYCODE_1, result?.keyCode)
        assertEquals('1'.code, result?.unicodeChar)
        assertEquals("1", result?.displayChar)
        assertEquals(native.eventTime, result?.eventTimeMillis)
    }

    @Test
    fun `host ignores back and volume keys`() {
        val back = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        val volume = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)

        assertNull(toR65CHidCapturedKeyEventIfRelevant(true, R65CHidRawCaptureState.Armed, back))
        assertNull(toR65CHidCapturedKeyEventIfRelevant(true, R65CHidRawCaptureState.Armed, volume))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.R65CHidRawCaptureHostTest"
```

Expected: FAIL with unresolved reference for `toR65CHidCapturedKeyEventIfRelevant`.

- [ ] **Step 3: Write the minimal host adapter**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import android.view.KeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState

internal fun toR65CHidCapturedKeyEventIfRelevant(
    isListening: Boolean,
    currentState: R65CHidRawCaptureState,
    keyEvent: KeyEvent,
): R65CHidCapturedKeyEvent? {
    if (!isListening) return null
    if (currentState != R65CHidRawCaptureState.Armed && currentState != R65CHidRawCaptureState.Capturing) return null
    if (keyEvent.action != KeyEvent.ACTION_DOWN) return null

    if (keyEvent.keyCode in setOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
        )) {
        return null
    }

    val displayChar = when {
        keyEvent.keyCode == KeyEvent.KEYCODE_ENTER || keyEvent.unicodeChar == '\n'.code -> "\\n"
        keyEvent.unicodeChar == 0 -> ""
        else -> keyEvent.unicodeChar.toChar().toString()
    }

    return R65CHidCapturedKeyEvent(
        keyCode = keyEvent.keyCode,
        unicodeChar = keyEvent.unicodeChar,
        action = keyEvent.action,
        displayChar = displayChar,
        eventTimeMillis = keyEvent.eventTime,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.R65CHidRawCaptureHostTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt
git commit -m "feat(nfctest): add host raw HID capture adapter"
```

### Task 3: Refactor raw-validation ViewModel to listener semantics

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`

- [ ] **Step 1: Write the failing test for armed/capturing lifecycle**

```kotlin
@Test
fun `start listening arms validation and stop listening returns idle`() {
    val viewModel = R65CHidRawValidationViewModel(
        nowProvider = { fixedNow },
        completionDelayMillis = 400L,
    )

    viewModel.startListening()
    assertEquals(R65CHidRawCaptureState.Armed, viewModel.panelState.value.captureState)
    assertEquals(true, viewModel.panelState.value.isListening)

    viewModel.stopListening()
    assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
    assertEquals(false, viewModel.panelState.value.isListening)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: FAIL because `startListening()` / `stopListening()` do not exist yet.

- [ ] **Step 3: Refactor the ViewModel**

```kotlin
class R65CHidRawValidationViewModel @Inject constructor() : ViewModel() {
    // keep existing constructor seam

    fun startListening() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                isListening = true,
                captureState = if (it.currentSessionEvents.isEmpty()) {
                    R65CHidRawCaptureState.Armed
                } else {
                    R65CHidRawCaptureState.Capturing
                },
            )
        }
    }

    fun stopListening() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                isListening = false,
                captureState = R65CHidRawCaptureState.Idle,
            )
        }
    }

    fun onHostCapturedKey(event: R65CHidCapturedKeyEvent) {
        if (!_panelState.value.isListening) return
        // append event, move Armed -> Capturing, Enter/timeout completion, etc.
    }

    fun requestRefocus() {
        // only increments token; no authority over listening state
    }

    fun clearLastSession() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                textFieldValue = "",
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = null,
                lastSessionAssembledChars = null,
                lastSessionEvents = emptyList(),
                lastCompletedReason = null,
                candidateValues = emptyList(),
                lastCompletedAt = null,
                captureState = if (it.isListening) R65CHidRawCaptureState.Armed else R65CHidRawCaptureState.Idle,
            )
        }
    }
}
```

Also update tests so they cover:
- `startListening()` / `stopListening()`
- `onHostCapturedKey(...)` transitions `Armed -> Capturing`
- completion while listening keeps `isListening = true` and returns to a completed state
- `clearLastSession()` returns to `Armed` when still listening

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt
git commit -m "refactor(nfctest): align raw validation view model with host capture"
```

### Task 4: Refactor raw-validation panel into a display/control surface

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`

- [ ] **Step 1: Write the failing panel test for listening controls**

```kotlin
@Test
fun panel_shows_listening_controls() {
    composeRule.setContent {
        LongCareTheme {
            R65CHidRawValidationPanel(
                state = R65CHidRawValidationState(
                    captureState = R65CHidRawCaptureState.Idle,
                    isListening = false,
                ),
                onTextFieldValueChanged = {},
                onFocusChanged = {},
                onStartListening = {},
                onStopListening = {},
                onRequestRefocus = {},
                onClearSession = {},
            )
        }
    }

    composeRule.onNodeWithTag("r65c_raw_start_button").assertExists()
    composeRule.onNodeWithTag("r65c_raw_stop_button").assertExists()
}
```

- [ ] **Step 2: Run androidTest compile to verify it fails**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because the panel signature and tags do not yet match the new control model.

- [ ] **Step 3: Refactor the panel**

```kotlin
@Composable
internal fun R65CHidRawValidationPanel(
    state: R65CHidRawValidationState,
    onTextFieldValueChanged: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onRequestRefocus: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // no raw authority here anymore
    // host capture happens above this panel

    Button(onClick = onStartListening, modifier = Modifier.testTag("r65c_raw_start_button")) { Text("开始监听") }
    Button(onClick = onStopListening, modifier = Modifier.testTag("r65c_raw_stop_button")) { Text("停止监听") }
    Button(onClick = onRequestRefocus, modifier = Modifier.testTag("r65c_raw_refocus_button")) { Text("重新聚焦") }
    Button(onClick = onClearSession, modifier = Modifier.testTag("r65c_raw_clear_button")) { Text("清空会话") }
}
```

Update the existing panel test file so it covers:
- start-listening callback
- stop-listening callback
- clear-session callback
- render-value assertions for text/assembled/candidates

- [ ] **Step 4: Run androidTest compile to verify it passes**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt
git commit -m "refactor(nfctest): make raw validation panel host-driven"
```

### Task 5: Install host capture at NfcTestScreen level and rewire body

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`
- Reuse: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`

- [ ] **Step 1: Write a failing body-level regression test**

```kotlin
@Test
fun body_shows_smoke_panel_and_host_driven_raw_validation_panel() {
    composeRule.setContent {
        LongCareTheme {
            NfcTestBody(
                enabled = true,
                r65cPanelState = R65CHidPanelState(captureState = R65CHidCaptureState.ReadyForScan),
                rawValidationState = R65CHidRawValidationState(captureState = R65CHidRawCaptureState.Idle, isListening = false),
                onR65CInputChanged = {},
                onR65CFocusChanged = {},
                onR65CRequestRefocus = {},
                onR65CClearResult = {},
                onRawTextFieldValueChanged = {},
                onRawFocusChanged = {},
                onRawStartListening = {},
                onRawStopListening = {},
                onRawRequestRefocus = {},
                onRawClearSession = {},
            )
        }
    }

    composeRule.onNodeWithText("R65C HID 键盘口测试").assertExists()
    composeRule.onNodeWithText("R65C 原始 HID 输出验证").assertExists()
    composeRule.onNodeWithTag("r65c_raw_start_button").assertExists()
}
```

- [ ] **Step 2: Run androidTest compile to verify it fails**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL because the screen/body signatures do not yet match the new host-driven raw panel contract.

- [ ] **Step 3: Rewire the screen and body**

```kotlin
// NfcTestScreen.kt
val rawValidationViewModel: R65CHidRawValidationViewModel = hiltViewModel()
val rawValidationState by rawValidationViewModel.panelState.collectAsStateWithLifecycle()

Box(
    modifier = Modifier
        .fillMaxSize()
        .onPreviewKeyEvent { keyEvent ->
            toR65CHidCapturedKeyEventIfRelevant(
                isListening = rawValidationState.isListening,
                currentState = rawValidationState.captureState,
                keyEvent = keyEvent.nativeKeyEvent,
            )?.let(rawValidationViewModel::onHostCapturedKey)
            false
        }
) {
    // existing scaffold content
}
```

```kotlin
// NfcTestScreenContent.kt
internal fun NfcTestBody(
    enabled: Boolean,
    r65cPanelState: R65CHidPanelState,
    rawValidationState: R65CHidRawValidationState = R65CHidRawValidationState(),
    onR65CInputChanged: (String) -> Unit,
    onR65CFocusChanged: (Boolean) -> Unit,
    onR65CRequestRefocus: () -> Unit,
    onR65CClearResult: () -> Unit,
    onRawTextFieldValueChanged: (String) -> Unit = {},
    onRawFocusChanged: (Boolean) -> Unit = {},
    onRawStartListening: () -> Unit = {},
    onRawStopListening: () -> Unit = {},
    onRawRequestRefocus: () -> Unit = {},
    onRawClearSession: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // render smoke panel first, then host-driven raw panel
}
```

- [ ] **Step 4: Run the final verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationContractsTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.R65CHidRawCaptureHostTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawCandidateMapperTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected:
- all four unit test commands PASS
- `compileDebugAndroidTestKotlin` PASSes
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt
git commit -m "feat(nfctest): wire host-driven raw HID validation into screen"
```

## Self-Review

### Spec coverage

- host-level raw key capture is introduced explicitly: covered by Task 2 and Task 5
- old TextField-only raw capture is no longer the authority: covered by Task 4 and Task 5
- smoke panel remains: covered by Task 5
- raw validation remains diagnostic only: no task touches formal NFC workflow files
- `开始监听 / 停止监听 / 清空会话` controls are introduced: covered by Task 4
- comparison between text-field result and host-captured output remains visible: covered by Task 4 and Task 5

### Placeholder scan

- No `TODO`, `TBD`, or vague “implement later” instructions remain
- Each code step has concrete file paths, code, and commands

### Type consistency

- New host-driven surface is consistently named:
  - `R65CHidRawCaptureHost`
  - `toR65CHidCapturedKeyEventIfRelevant(...)`
  - `startListening()` / `stopListening()` / `onHostCapturedKey(...)`
- Raw panel callback names are consistently host-driven:
  - `onRawStartListening`
  - `onRawStopListening`
  - `onRawRequestRefocus`
  - `onRawClearSession`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-09-r65c-raw-hid-output-validation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

After execution, use the acceptance checklist:

- [`../specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md`](../specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md)
