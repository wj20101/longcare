# R65C Business Fallback Integration Refined Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate `R65C HID` into the formal NFC workflow as the automatic fallback on devices without NFC, while reusing the existing `EXTERNAL_RFID` business boundary and publishing only business-valid Tag IDs.

**Architecture:** Keep the current `ScanMode.SYSTEM_NFC / ScanMode.EXTERNAL_RFID` split. Add a small `R65C HID` fallback adapter in the workflow UI layer to collect stable HID text, then pass candidate values into the external-reader boundary for normalization, strict Tag ID validation, duplicate suppression, and consecutive-failure escalation. Downstream business logic continues to consume only `AppEvent.TagScanned(tagId, ScanSource.EXTERNAL_RFID)`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `StateFlow`, `AppEventBus`, Hilt, JUnit4, MockK, existing NFC workflow and external RFID infrastructure.

---

## File Structure

### New files

- `app/src/main/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilter.kt`
  - Small, focused boundary object for strict `R65C HID` candidate normalization, NFC-equivalent Tag ID validation, duplicate suppression, and invalid-capture streak handling.
- `app/src/test/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilterTest.kt`
  - Unit tests for valid input, invalid input, duplicate suppression, and invalid-streak behavior.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt`
  - Formal workflow-only HID input collector that captures stable text input for `R65C` without exposing test UI.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContractsTest.kt`
  - Small unit tests for `selectScanMode(false)` and reader-state defaults if no suitable test already exists.

### Modified files

- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
  - Extend the interface so the workflow can submit `R65C HID` candidate payloads into the existing `EXTERNAL_RFID` boundary.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
  - Reuse one business-validation path for USB payloads and `R65C HID` payloads; publish `TagScanned` only after strict validation.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
  - Tighten or narrow its role so it supports NFC-equivalent normalization instead of broad alphanumeric acceptance.
- `app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt`
  - Update tests to reflect the stricter normalization contract or the reduced parser responsibility.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
  - Expose a workflow-safe entry point that accepts `R65C HID` candidate strings and routes them through the external-reader boundary only when `scanMode == EXTERNAL_RFID`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
  - Attach the `R65C HID` workflow adapter only for non-NFC devices.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
  - Render the hidden or unobtrusive workflow fallback collector only in the `EXTERNAL_RFID` path.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
  - Refine copy so the non-NFC path speaks about `R65C` rather than a generic Type-C external reader.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
  - Update workflow-copy expectations for the `R65C` fallback path.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt`
  - Add or adjust tests so valid external scans still drive the same business flow and reader-state semantics remain consistent.
- `app/src/main/res/values/strings.xml`
  - Update formal workflow copy for the `R65C` fallback experience.

### Shared files to verify but keep stable unless tests force change

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
  - Downstream business flow should remain unified; avoid introducing a separate `R65C` business branch.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
  - Lifecycle start and stop should remain the source-control point for `SYSTEM_NFC` and `EXTERNAL_RFID`.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt`
  - Source of truth for the system NFC Tag ID format.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidInputTestPanel.kt`
  - Existing stable test surface remains test-only; do not repurpose it directly into the business UI.

## Design Constraints Locked In

- Devices with NFC support stay on `SYSTEM_NFC`.
- Devices without NFC support stay on `EXTERNAL_RFID`.
- `R65C HID` is an input source for the existing external-reader boundary, not a new scan mode.
- Only NFC-equivalent Tag IDs may publish `AppEvent.TagScanned(..., ScanSource.EXTERNAL_RFID)`.
- Single invalid HID capture is silent.
- A valid capture resets the invalid-streak counter.
- Repeated invalid captures escalate to `ReaderUiState.DeviceError`.
- Workflow page enters ready-to-scan behavior automatically; no manual start button is added.

### Task 1: Add strict business validation for `R65C HID` candidates

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilter.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilterTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt`

- [ ] **Step 1: Write the failing filter tests for valid, invalid, duplicate, and streak cases**

```kotlin
package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R65cBusinessFallbackFilterTest {

    @Test
    fun `accepts uppercase hex tag ids that match NFC-style format`() {
        val filter = R65cBusinessFallbackFilter()

        val result = filter.consume(rawPayload = " 0426fafa051f91 ")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), result)
    }

    @Test
    fun `rejects payload containing non hex characters`() {
        val filter = R65cBusinessFallbackFilter()

        val result = filter.consume(rawPayload = "901948不EA8想0想")

        assertEquals(R65cBusinessFallbackResult.Invalid(1), result)
    }

    @Test
    fun `suppresses duplicate valid payload inside duplicate window`() {
        val filter = R65cBusinessFallbackFilter(
            nowProvider = { 1000L },
            duplicateWindowMillis = 1500L,
        )

        val first = filter.consume(rawPayload = "0426FAFA051F91")
        val second = filter.consume(rawPayload = "0426FAFA051F91")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), first)
        assertEquals(R65cBusinessFallbackResult.DuplicateSuppressed("0426FAFA051F91"), second)
    }

    @Test
    fun `returns escalated invalid state after threshold`() {
        val filter = R65cBusinessFallbackFilter(invalidThreshold = 3)

        assertEquals(R65cBusinessFallbackResult.Invalid(1), filter.consume("中文"))
        assertEquals(R65cBusinessFallbackResult.Invalid(2), filter.consume("abc-123"))
        assertEquals(R65cBusinessFallbackResult.DeviceError(streak = 3), filter.consume("123456789"))
    }

    @Test
    fun `valid payload resets invalid streak`() {
        val filter = R65cBusinessFallbackFilter(invalidThreshold = 3)

        filter.consume("中文")
        val valid = filter.consume("0426FAFA051F91")
        val nextInvalid = filter.consume("中文")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), valid)
        assertEquals(R65cBusinessFallbackResult.Invalid(1), nextInvalid)
    }
}
```

- [ ] **Step 2: Run the new test class to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.R65cBusinessFallbackFilterTest"
```

Expected: FAIL because `R65cBusinessFallbackFilter` does not exist yet.

- [ ] **Step 3: Implement the minimal strict filter**

```kotlin
package com.ytone.longcare.common.utils

import java.util.Locale

internal sealed class R65cBusinessFallbackResult {
    data class Valid(val tagId: String) : R65cBusinessFallbackResult()
    data class DuplicateSuppressed(val tagId: String) : R65cBusinessFallbackResult()
    data class Invalid(val streak: Int) : R65cBusinessFallbackResult()
    data class DeviceError(val streak: Int) : R65cBusinessFallbackResult()
}

internal class R65cBusinessFallbackFilter(
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val duplicateWindowMillis: Long = 1500L,
    private val invalidThreshold: Int = 3,
) {
    private var invalidStreak: Int = 0
    private var lastPublishedTagId: String? = null
    private var lastPublishedAtMillis: Long = 0L

    fun consume(rawPayload: String): R65cBusinessFallbackResult {
        val normalized = normalize(rawPayload)
        if (normalized == null) {
            invalidStreak += 1
            return if (invalidStreak >= invalidThreshold) {
                R65cBusinessFallbackResult.DeviceError(invalidStreak)
            } else {
                R65cBusinessFallbackResult.Invalid(invalidStreak)
            }
        }

        val now = nowProvider()
        if (normalized == lastPublishedTagId && now - lastPublishedAtMillis <= duplicateWindowMillis) {
            invalidStreak = 0
            return R65cBusinessFallbackResult.DuplicateSuppressed(normalized)
        }

        invalidStreak = 0
        lastPublishedTagId = normalized
        lastPublishedAtMillis = now
        return R65cBusinessFallbackResult.Valid(normalized)
    }

    private fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(Regex("[\\s:_-]+"), "")
            .uppercase(Locale.ROOT)

        val isHex = normalized.isNotBlank() && normalized.all { it in '0'..'9' || it in 'A'..'F' }
        val hasValidLength = normalized.length == 8 || normalized.length == 14
        return normalized.takeIf { isHex && hasValidLength }
    }
}
```

- [ ] **Step 4: Tighten `ExternalRfidTagParser` so it no longer accepts broad alphanumeric payloads**

```kotlin
@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(Regex("[\\s:_-]+"), "")
            .uppercase(Locale.ROOT)

        return normalized.takeIf {
            it.isNotBlank() &&
                it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } &&
                (it.length == 8 || it.length == 14)
        }
    }
}
```

- [ ] **Step 5: Update parser tests to the stricter contract**

```kotlin
    @Test
    fun `normalize trims separators and uppercases NFC style hex ids`() {
        assertEquals("0426FAFA051F91", parser.normalize(" 0426-fa fa_051f91 "))
    }

    @Test
    fun `normalize rejects blank non hex and invalid length payloads`() {
        assertNull(parser.normalize("   "))
        assertNull(parser.normalize("01-AB-Z9"))
        assertNull(parser.normalize("123456789"))
        assertNull(parser.normalize("中文"))
    }
```

- [ ] **Step 6: Run the filter and parser tests to verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.R65cBusinessFallbackFilterTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"
```

Expected: PASS for both commands.

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilter.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/R65cBusinessFallbackFilterTest.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt
git commit -m "feat(nfc): add strict R65C fallback tag validation"
```

### Task 2: Route `R65C HID` candidates through the external-reader boundary

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt`

- [ ] **Step 1: Add failing workflow helper coverage for external reader event semantics**

Add this to `NfcScanWorkflowHelpersTest.kt`:

```kotlin
    @Test
    fun `reduceReaderUiState turns reader errors into device error for external mode`() {
        val next = reduceReaderUiState(
            currentMode = ScanMode.EXTERNAL_RFID,
            event = AppEvent.ReaderError("R65C读卡异常，请重试", ScanSource.EXTERNAL_RFID),
            currentReaderState = ReaderUiState.Ready,
        )

        assertEquals(ReaderUiState.DeviceError("R65C读卡异常，请重试"), next)
    }
```

- [ ] **Step 2: Run the helper test class to confirm baseline behavior**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
```

Expected: PASS after the added assertion, because helper semantics should already support `ReaderError`.

- [ ] **Step 3: Extend the external-reader manager interface with a workflow candidate entry point**

```kotlin
interface ExternalRfidReaderManager {
    fun start(activity: Activity)
    fun stop(activity: Activity)
    fun submitHidCandidate(rawPayload: String)
}
```

- [ ] **Step 4: Reuse one strict publish path inside `UsbExternalRfidReaderManager`**

```kotlin
@Singleton
class UsbExternalRfidReaderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appEventBus: AppEventBus,
    private val filter: R65cBusinessFallbackFilter,
) : ExternalRfidReaderManager {
    // existing members stay

    override fun submitHidCandidate(rawPayload: String) {
        publishCandidate(rawPayload)
    }

    internal fun publishRawPayload(rawPayload: String) {
        publishCandidate(rawPayload)
    }

    private fun publishCandidate(rawPayload: String) {
        when (val result = filter.consume(rawPayload)) {
            is R65cBusinessFallbackResult.Valid -> {
                scope.launch {
                    appEventBus.send(AppEvent.TagScanned(result.tagId, ScanSource.EXTERNAL_RFID))
                }
            }

            is R65cBusinessFallbackResult.DeviceError -> {
                publishReaderError("R65C读卡异常，请重试")
            }

            is R65cBusinessFallbackResult.Invalid,
            is R65cBusinessFallbackResult.DuplicateSuppressed,
            -> Unit
        }
    }
}
```

- [ ] **Step 5: Run focused unit tests for parser, filter, and workflow helpers**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.R65cBusinessFallbackFilterTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
```

Expected: PASS for all three commands.

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt
git commit -m "feat(nfc): route R65C HID through external reader boundary"
```

### Task 3: Add the workflow-side `R65C HID` collector for non-NFC devices

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`

- [ ] **Step 1: Create a failing UI compilation target by referencing the new composable in the workflow layout**

Add this call inside `NfcWorkflowBodyContent(...)`, below `SignInContentCard(...)` and above `NfcWorkflowDebugMockButton(...)`:

```kotlin
        if (scanMode == ScanMode.EXTERNAL_RFID) {
            R65cWorkflowHidFallbackField(
                readerUiState = readerUiState,
                onInputChanged = nfcViewModel::onR65cFallbackInputChanged,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
```

Expected compile state before the new composable and ViewModel method exist: FAIL.

- [ ] **Step 2: Add the workflow ViewModel entry point**

```kotlin
    fun onR65cFallbackInputChanged(rawPayload: String) {
        if (_scanMode.value != ScanMode.EXTERNAL_RFID) return
        if (rawPayload.isBlank()) return
        _readerUiState.value = ReaderUiState.Reading
        externalRfidReaderManager.submitHidCandidate(rawPayload)
    }
```

- [ ] **Step 3: Create the minimal workflow fallback collector composable**

```kotlin
package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.nfc.vm.ReaderUiState

@Composable
internal fun R65cWorkflowHidFallbackField(
    readerUiState: ReaderUiState,
    onInputChanged: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(readerUiState) {
        if (readerUiState == ReaderUiState.Ready || readerUiState == ReaderUiState.Reading) {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        value = "",
        onValueChange = onInputChanged,
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(0.dp)
            .focusRequester(focusRequester),
        label = null,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
    )
}
```

- [ ] **Step 4: Run compilation to verify the workflow wiring builds**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/R65cWorkflowHidFallbackField.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt
git commit -m "feat(nfc): add R65C HID workflow fallback collector"
```

### Task 4: Update workflow copy for `R65C` fallback mode

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Write failing copy tests for the `R65C`-specific external-reader text**

Replace the test names and expectations with copy that references `R65C` rather than generic Type-C reader language:

```kotlin
    @Test
    fun `external disconnected copy instructs the user to prepare R65C`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Disconnected,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT, copy.bottomHintKey)
    }

    @Test
    fun `external ready copy instructs the user to scan on R65C`() {
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
    <string name="nfc_external_reader_prompt">请准备 R65C 读卡器</string>
    <string name="nfc_external_reader_disconnected">R65C 未就绪</string>
    <string name="nfc_external_reader_disconnected_hint">请确认 R65C 已连接并可输入后再刷卡</string>
    <string name="nfc_external_reader_ready_prompt">请将卡片放在 R65C 感应区</string>
    <string name="nfc_external_reader_ready">R65C 已就绪</string>
    <string name="nfc_external_reader_ready_hint">请在 R65C 上完成刷卡</string>
```

- [ ] **Step 3: Run the copy test class**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt \
  app/src/main/res/values/strings.xml
git commit -m "feat(nfc): update workflow copy for R65C fallback"
```

### Task 5: Run final verification for the refined fallback integration

**Files:**
- Reuse: `docs/superpowers/specs/2026-04-11-r65c-business-fallback-integration-refine-design.md`

- [ ] **Step 1: Run the focused verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.R65cBusinessFallbackFilterTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected:

- all four focused unit test commands PASS
- `compileDebugKotlin` PASSes
- `lintDebug` PASSes

- [ ] **Step 2: Manually verify the refined business behavior on a non-NFC device**

Confirm:

- workflow enters `EXTERNAL_RFID` automatically when NFC is unavailable
- page is ready to scan without a manual start action
- a valid `R65C` input such as `0426FAFA051F91` reaches the business event flow
- one noisy or malformed input is ignored
- repeated malformed input eventually produces `DeviceError`

- [ ] **Step 3: Stop only when the fallback path is business-equivalent**

Expected result:

- valid `R65C` fallback scans are business-equivalent to other `EXTERNAL_RFID` scans
- invalid HID noise never reaches `AppEvent.TagScanned`
- workflow copy and state behavior match the refined design

## Self-Review

### Spec coverage

- activation rule reuses `EXTERNAL_RFID`: covered by Tasks 2 and 3
- business-valid Tag ID must match NFC-style uppercase hex format: covered by Task 1
- invalid single scans are silent and repeated failures escalate: covered by Tasks 1 and 2
- workflow enters ready state automatically without a start button: covered by Task 3
- business UI copy should describe `R65C`: covered by Task 4
- raw HID diagnostics remain test-only: preserved by avoiding changes to test surfaces in this plan

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation notes remain
- Every code-changing step includes concrete code snippets
- Every verification step includes exact commands and expected results

### Type consistency

- `submitHidCandidate(rawPayload: String)` is used consistently across the reader manager and workflow ViewModel steps
- `R65cBusinessFallbackFilter` and `R65cBusinessFallbackResult` are introduced before later tasks reference them
- `ScanMode.EXTERNAL_RFID` and `ReaderUiState` semantics match the refined design throughout the plan

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-11-r65c-business-fallback-integration-refine.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
