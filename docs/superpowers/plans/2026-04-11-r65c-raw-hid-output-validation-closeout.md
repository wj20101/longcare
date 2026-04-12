# R65C Raw HID Output Validation Close-Out Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out the remaining gaps in the host-driven `R65C 原始 HID 输出验证` flow so the feature compiles, the remaining behavior matches the approved plan, and the final verification suite passes.

**Architecture:** Keep the existing host-driven direction already present in the workspace: `NfcTestScreen` remains the raw key capture host, `R65CHidRawValidationPanel` remains a display and control surface, and `R65CHidRawValidationViewModel` remains the owner of listener lifecycle and session completion. The close-out work is intentionally narrow: fix the last behavior mismatch, repair the broken `androidTest` compile, add the missing regression coverage, and then run the full verification set.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `KeyEvent`, `StateFlow`, Hilt ViewModel, JUnit4, Compose UI instrumentation tests, Gradle.

---

## Review Summary

### Already aligned with the approved plan

- Host-level raw key capture has been moved up to [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`](../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt).
- The raw panel has been simplified into a display and control surface in [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`](../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt).
- Listener semantics are already present in [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`](../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt) and [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt`](../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationContracts.kt).
- The new host adapter already exists in [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`](../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt).

### Remaining close-out gaps

- `clearLastSession()` still leaves stale `textFieldValue` behind, which does not match the approved clear-session behavior.
- `compileDebugAndroidTestKotlin` currently fails because [`app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`](../../app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt) is missing imports for `R65CHidPanelState` and `R65CHidCaptureState`.
- Regression coverage is still missing for two important boundary behaviors:
  - host adapter ignores non-`ACTION_DOWN` events
  - ViewModel ignores later host keys after `stopListening()`
- Final verification has not been run cleanly against the full accepted command set.

## File Structure

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
  - Fix `clearLastSession()` so the live text field is cleared together with session results.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`
  - Add close-out regression tests for clear semantics and stop-listening behavior.
- `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`
  - Repair missing imports so the screen/body regression test compiles again.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`
  - Add regression coverage for non-`ACTION_DOWN` filtering.

### Shared files to verify but not rewrite unless tests demand it

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  - Screen-level host capture wiring should stay unchanged unless final verification proves otherwise.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  - Body wiring should stay unchanged unless final verification proves otherwise.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`
  - Panel behavior should stay host-driven and display-only.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`
  - Adapter logic should already be correct; only extend tests first.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawCandidateMapper.kt`
  - Candidate generation is already in scope for final verification, not redesign.

## Verification Note

During review, the first isolated `testDebugUnitTest` invocation occasionally failed with Kotlin incremental or KSP cache noise, then passed on immediate rerun without source changes. Do not change source code on the first such failure. Rerun the same command once from a warm daemon before treating it as a real code failure.

### Task 1: Fix clear-session semantics in the ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`

- [ ] **Step 1: Add failing regression tests for clear-session behavior**

```kotlin
    @Test
    fun `clearLastSession clears live text and last-session state while listening`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.onTextFieldValueChanged("AB")
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )
        viewModel.clearLastSession()

        assertEquals(R65CHidRawCaptureState.Armed, viewModel.panelState.value.captureState)
        assertEquals(true, viewModel.panelState.value.isListening)
        assertEquals("", viewModel.panelState.value.textFieldValue)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
        assertEquals(null, viewModel.panelState.value.lastSessionTextFieldValue)
        assertEquals(null, viewModel.panelState.value.lastSessionAssembledChars)
    }

    @Test
    fun `clearLastSession while idle clears live text and stays idle`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.onTextFieldValueChanged("AB")
        viewModel.clearLastSession()

        assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
        assertEquals(false, viewModel.panelState.value.isListening)
        assertEquals("", viewModel.panelState.value.textFieldValue)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
    }
```

- [ ] **Step 2: Run the ViewModel test class to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: FAIL because `clearLastSession()` currently leaves `textFieldValue` unchanged.

- [ ] **Step 3: Update `clearLastSession()` to clear the live input buffer**

```kotlin
    fun clearLastSession() {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                captureState = if (state.isListening) {
                    R65CHidRawCaptureState.Armed
                } else {
                    R65CHidRawCaptureState.Idle
                },
                textFieldValue = "",
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = null,
                lastSessionAssembledChars = null,
                lastSessionEvents = emptyList(),
                lastCompletedReason = null,
                candidateValues = emptyList(),
                lastCompletedAt = null,
            )
        }
    }
```

- [ ] **Step 4: Run the ViewModel test class to verify it passes**

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
git commit -m "fix(nfctest): clear live raw input when resetting session"
```

### Task 2: Repair the raw validation `androidTest` compile break

**Files:**
- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt`

- [ ] **Step 1: Run the `androidTest` compile to confirm the current break**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: FAIL with unresolved references for `R65CHidPanelState` and `R65CHidCaptureState`.

- [ ] **Step 2: Add the missing imports used by the body regression test**

```kotlin
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
```

After the change, the import block near the top of the file should include:

```kotlin
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateKind
import com.ytone.longcare.features.nfctest.vm.R65CHidCandidateValue
import com.ytone.longcare.features.nfctest.vm.R65CHidCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidCompletionReason
import com.ytone.longcare.features.nfctest.vm.R65CHidPanelState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawCaptureState
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationState
```

- [ ] **Step 3: Re-run the `androidTest` compile**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanelTest.kt
git commit -m "test(nfctest): repair raw validation panel androidTest imports"
```

### Task 3: Add the missing boundary regressions for host-driven semantics

**Files:**
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt`

- [ ] **Step 1: Add a regression test that proves the host adapter ignores non-`ACTION_DOWN` input**

```kotlin
    @Test
    fun `host ignores non action down event`() {
        val keyUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_1)

        val result = toR65CHidCapturedKeyEventIfRelevant(
            isListening = true,
            currentState = R65CHidRawCaptureState.Armed,
            keyEvent = keyUp,
        )

        assertNull(result)
    }
```

- [ ] **Step 2: Add a regression test that proves `stopListening()` blocks later host capture**

```kotlin
    @Test
    fun `stopListening ignores later host keys`() {
        val viewModel = R65CHidRawValidationViewModel(
            nowProvider = { fixedNow },
            completionDelayMillis = 400L,
        )

        viewModel.startListening()
        viewModel.stopListening()
        viewModel.onHostCapturedKey(
            R65CHidCapturedKeyEvent(
                keyCode = 29,
                unicodeChar = 'A'.code,
                action = 0,
                displayChar = "A",
                eventTimeMillis = 1L,
            ),
        )

        assertEquals(R65CHidRawCaptureState.Idle, viewModel.panelState.value.captureState)
        assertEquals(false, viewModel.panelState.value.isListening)
        assertTrue(viewModel.panelState.value.currentSessionEvents.isEmpty())
        assertEquals("", viewModel.panelState.value.currentSessionAssembledChars)
    }
```

- [ ] **Step 3: Run the focused host and ViewModel unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.R65CHidRawCaptureHostTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"
```

Expected: PASS for both commands. These are regression tests for behavior that should already be correct after the host-driven refactor.

- [ ] **Step 4: Commit**

```bash
git add \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHostTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModelTest.kt
git commit -m "test(nfctest): cover host-driven raw validation boundaries"
```

### Task 4: Run final verification and close against the acceptance checklist

**Files:**
- Reuse: `docs/superpowers/specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md`

- [ ] **Step 1: Run the accepted verification set in order**

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

- [ ] **Step 2: Validate the manual acceptance points that depend on runtime behavior**

Check against:

```text
docs/superpowers/specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md
```

At minimum, confirm:

- host capture is still wired at `NfcTestScreen`
- `开始监听 / 停止监听 / 重新聚焦 / 清空会话` are present
- the smoke-test panel remains visible beside the raw validation panel
- `clearLastSession()` now clears the live input field as well as session results

- [ ] **Step 3: Stop only when the acceptance checklist and verification commands agree**

Expected result:

- the feature is ready for implementation closure
- the close-out work no longer depends on plan interpretation
- the next action can be merge or PR preparation rather than additional refactor design

## Self-Review

### Spec coverage

- review finding: stale live text after clear: covered by Task 1
- review finding: broken `androidTest` compile: covered by Task 2
- review finding: missing host boundary regression: covered by Task 3
- review finding: missing stop-listening regression: covered by Task 3
- review finding: final accepted verification not yet clean: covered by Task 4

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation notes remain
- Every code-changing step contains the exact test or implementation snippet needed
- Every verification step contains an exact command and expected result

### Type consistency

- `startListening()`, `stopListening()`, `onHostCapturedKey(...)`, and `clearLastSession()` are named consistently with the current workspace
- `R65CHidPanelState` and `R65CHidCaptureState` are the exact missing imports used by the `androidTest` body regression
- `R65CHidRawCaptureState.Idle` and `R65CHidRawCaptureState.Armed` are used consistently across the close-out tests

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-r65c-raw-hid-output-validation-closeout.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
