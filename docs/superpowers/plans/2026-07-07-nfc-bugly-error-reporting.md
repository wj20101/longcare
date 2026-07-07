# NFC Bugly Error Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every user-visible NFC workflow failure is reported to Bugly exactly once while preserving inline error display.

**Architecture:** Keep reporting in the NFC ViewModel/delegate layer, not Compose UI. Add reporting metadata to `NfcSignInUiState.Error`, keep existing API/exception reporting marked as already reported, and route unreported user-visible failures through one NFC diagnostic helper.

**Tech Stack:** Kotlin, Android, Jetpack Compose state, Tencent Bugly through `DiagnosticEventTracker`, Coroutines, JUnit4, Gradle.

## Global Constraints

- NFC workflow errors that end in `NfcSignInUiState.Error` must be Bugly-reported.
- Existing API/exception reports must not be duplicated.
- Do not send raw NFC ids, names, identity numbers, photos, image keys, tokens, or full URLs.
- Safe metadata may include order ids, plan ids, sign-in mode, scan source, stage/event names, message text, NFC id length/hash, and boolean presence flags.
- Keep inline error display behavior unchanged.
- Do not change Bugly initialization or global diagnostic infrastructure.

---

## File Structure

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
  - Adds the `buglyReported` metadata flag to `NfcSignInUiState.Error`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnostics.kt`
  - Adds helper APIs for already-reported Error states and fallback user-visible Bugly reporting.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`
  - Centralizes direct `showError()` behavior so unreported user-visible errors are reported once.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`
  - Marks existing API failure/exception Error states as already reported.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`
  - Marks existing end-order submit Error states as already reported.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
  - Supplies context to fallback `showError()` calls and marks already-reported bind-location errors.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanLocationActivationWorkflow.kt`
  - Marks already-reported order-detail lookup errors.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnosticsTest.kt`
  - Covers helper behavior, safe extras, and reported Error metadata.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlersTest.kt`
  - Covers API failure/exception result handlers marking Error states as already reported.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegateTest.kt`
  - Covers fallback user-visible reporting through `showError()`.

---

### Task 1: Add Error Reporting Metadata and Diagnostic Helpers

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnostics.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnosticsTest.kt`

**Interfaces:**
- Consumes:
  - `DiagnosticEventTracker.trackError(category, event, description, throwable, extras)`
  - `OrderKey(orderId: Long, planId: Long)`
  - `SignInMode`
- Produces:
  - `NfcSignInUiState.Error.buglyReported: Boolean`
  - `NfcUserVisibleErrorReport(event: String, description: String, extras: Map<String, Any?>)`
  - `reportedNfcError(message: String): NfcSignInUiState.Error`
  - `buildNfcUserVisibleErrorReport(...)`
  - `sendNfcUserVisibleErrorReport(report: NfcUserVisibleErrorReport)`
  - `reportUserVisibleNfcError(...)`

- [ ] **Step 1: Write failing diagnostic helper tests**

Create `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnosticsTest.kt`:

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDiagnosticsTest {

    @Test
    fun `new error states are unreported by default`() {
        val state = NfcSignInUiState.Error("定位失败")

        assertFalse(state.buglyReported)
    }

    @Test
    fun `reportedNfcError marks existing diagnostic paths as reported`() {
        val state = reportedNfcError("接口失败")

        assertEquals("接口失败", state.message)
        assertTrue(state.buglyReported)
    }

    @Test
    fun `user visible report includes safe context and hashes nfc id`() {
        val report = buildNfcUserVisibleErrorReport(
            message = "请开启定位服务以获取位置信息",
            source = "scan_location_error",
            orderKey = OrderKey(orderId = 1001L, planId = 2002L),
            signInMode = SignInMode.END_ORDER,
            nfcDeviceId = "RAW_NFC_123456",
            extras = mapOf("hasLongitude" to false, "hasLatitude" to false)
        )

        assertEquals("nfc_user_visible_error", report.event)
        assertEquals("NFC用户可见错误", report.description)
        assertEquals(1001L, report.extras["orderId"])
        assertEquals(2002L, report.extras["planId"])
        assertEquals("END_ORDER", report.extras["signInMode"])
        assertEquals("scan_location_error", report.extras["source"])
        assertEquals("请开启定位服务以获取位置信息", report.extras["message"])
        assertEquals("RAW_NFC_123456".length, report.extras["nfcDeviceIdLength"])
        assertEquals("RAW_NFC_123456".hashCode(), report.extras["nfcDeviceIdHash"])
        assertFalse(report.extras.containsValue("RAW_NFC_123456"))
    }

    @Test
    fun `reportUserVisibleNfcError invokes reporter and returns reported error`() {
        var capturedReport: NfcUserVisibleErrorReport? = null

        val state = reportUserVisibleNfcError(
            message = "定位失败",
            source = "resume_permission_scan_location_error",
            orderKey = OrderKey(orderId = 10L, planId = 20L),
            signInMode = SignInMode.START_ORDER,
            reporter = { report -> capturedReport = report }
        )

        assertEquals("定位失败", state.message)
        assertTrue(state.buglyReported)
        assertEquals("nfc_user_visible_error", capturedReport?.event)
        assertEquals("resume_permission_scan_location_error", capturedReport?.extras?.get("source"))
    }
}
```

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.vm.NfcDiagnosticsTest'
```

Expected: FAIL at compile time because `buglyReported`, `NfcUserVisibleErrorReport`, `reportedNfcError`, `buildNfcUserVisibleErrorReport`, and `reportUserVisibleNfcError` do not exist.

- [ ] **Step 3: Add reporting metadata to Error state**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`.

Replace the current `Error` class with:

```kotlin
    data class Error(
        val message: String,
        val occurrenceId: Long = System.nanoTime(),
        val buglyReported: Boolean = false,
    ) : NfcSignInUiState()
```

Keep all other `NfcSignInUiState` classes unchanged.

- [ ] **Step 4: Add diagnostic helper APIs**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnostics.kt`.

Add this code after `trackNfcFailure()` and before `buildNfcExtras()`:

```kotlin
internal data class NfcUserVisibleErrorReport(
    val event: String,
    val description: String,
    val extras: Map<String, Any?>,
)

internal fun reportedNfcError(message: String): NfcSignInUiState.Error =
    NfcSignInUiState.Error(
        message = message,
        buglyReported = true,
    )

internal fun buildNfcUserVisibleErrorReport(
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
): NfcUserVisibleErrorReport =
    NfcUserVisibleErrorReport(
        event = "nfc_user_visible_error",
        description = "NFC用户可见错误",
        extras = buildNfcExtras(
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = extras + mapOf(
                "source" to source,
                "message" to message,
            ),
        ),
    )

internal fun reportUserVisibleNfcError(
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
    reporter: (NfcUserVisibleErrorReport) -> Unit = ::sendNfcUserVisibleErrorReport,
): NfcSignInUiState.Error {
    val report = buildNfcUserVisibleErrorReport(
        message = message,
        source = source,
        orderKey = orderKey,
        signInMode = signInMode,
        nfcDeviceId = nfcDeviceId,
        extras = extras,
    )
    reporter(report)
    return reportedNfcError(message)
}

internal fun sendNfcUserVisibleErrorReport(report: NfcUserVisibleErrorReport) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = report.event,
        description = report.description,
        extras = report.extras,
    )
}
```

- [ ] **Step 5: Run the helper tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.vm.NfcDiagnosticsTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 1**

Run:

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnostics.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcDiagnosticsTest.kt
git commit -m "feat(nfc): add Bugly reporting metadata"
```

Expected: commit succeeds with only Task 1 files staged.

---

### Task 2: Wire NFC Error Paths Through Reported and Fallback States

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanLocationActivationWorkflow.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlersTest.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegateTest.kt`

**Interfaces:**
- Consumes:
  - `reportedNfcError(message: String)`
  - `reportUserVisibleNfcError(...)`
  - `NfcSignInUiState.Error.buglyReported`
- Produces:
  - `NfcOrderWorkflowDelegate.showError(message, source, orderKey, signInMode, nfcDeviceId, buglyAlreadyReported, extras)`
  - API/exception result handlers that mark errors as already reported.
  - Direct frontend `showError()` paths that fallback-report to Bugly exactly once.

- [ ] **Step 1: Write failing result-handler tests**

Create `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlersTest.kt`:

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NfcOrderWorkflowResultHandlersTest {

    @Test
    fun `api exception error state is marked as already reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)

        applyOrderApiException(
            exception = ApiResult.Exception(IOException("网络断开")),
            uiState = uiState
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("网络断开", error.message)
        assertTrue(error.buglyReported)
    }

    @Test
    fun `api failure error state is marked as already reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)

        applyOrderApiFailure(
            failure = ApiResult.Failure(code = 4001, message = "NFC不匹配"),
            uiState = uiState
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("NFC不匹配", error.message)
        assertTrue(error.buglyReported)
    }
}
```

- [ ] **Step 2: Write failing delegate fallback test**

Create `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegateTest.kt`:

```kotlin
package com.ytone.longcare.features.nfc.vm

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcOrderWorkflowDelegateTest {

    @Test
    fun `applyUserVisibleError reports fallback error and marks state reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
        var capturedReport: NfcUserVisibleErrorReport? = null

        applyUserVisibleNfcError(
            uiState = uiState,
            message = "请开启定位服务以获取位置信息",
            source = "scan_location_error",
            reporter = { report -> capturedReport = report }
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("请开启定位服务以获取位置信息", error.message)
        assertTrue(error.buglyReported)
        assertEquals("nfc_user_visible_error", capturedReport?.event)
        assertEquals("scan_location_error", capturedReport?.extras?.get("source"))
    }
}
```

- [ ] **Step 3: Run the new tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.nfc.vm.NfcOrderWorkflowResultHandlersTest' \
  --tests 'com.ytone.longcare.features.nfc.vm.NfcOrderWorkflowDelegateTest'
```

Expected: FAIL because `applyUserVisibleNfcError()` does not exist and result handlers still create unreported Error states.

- [ ] **Step 4: Add a small helper for applying user-visible errors**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt`.

Add this top-level helper above `internal class NfcOrderWorkflowDelegate`:

```kotlin
internal fun applyUserVisibleNfcError(
    uiState: MutableStateFlow<NfcSignInUiState>,
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    buglyAlreadyReported: Boolean = false,
    extras: Map<String, Any?> = emptyMap(),
    reporter: (NfcUserVisibleErrorReport) -> Unit = ::sendNfcUserVisibleErrorReport,
) {
    uiState.value = if (buglyAlreadyReported) {
        reportedNfcError(message)
    } else {
        reportUserVisibleNfcError(
            message = message,
            source = source,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = extras,
            reporter = reporter,
        )
    }
}
```

Add this import to the same file:

```kotlin
import com.ytone.longcare.navigation.SignInMode
```

Then replace the existing `showError(message: String)` with:

```kotlin
    fun showError(
        message: String,
        source: String = "nfc_order_workflow_show_error",
        orderKey: OrderKey? = null,
        signInMode: SignInMode? = null,
        nfcDeviceId: String? = null,
        buglyAlreadyReported: Boolean = false,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        applyUserVisibleNfcError(
            uiState = uiState,
            message = message,
            source = source,
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            buglyAlreadyReported = buglyAlreadyReported,
            extras = extras,
        )
    }
```

- [ ] **Step 5: Mark API result handlers as already reported**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt`.

Replace these assignments:

```kotlin
uiState.value = NfcSignInUiState.Error(message)
uiState.value = NfcSignInUiState.Error(failure.message)
```

with:

```kotlin
uiState.value = reportedNfcError(message)
uiState.value = reportedNfcError(failure.message)
```

Leave the `ShowConfirmDialog` branch for failure code `3005` unchanged because it is not an Error state.

- [ ] **Step 6: Mark end-order submit errors as already reported**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt`.

Replace:

```kotlin
uiState.value = NfcSignInUiState.Error(message)
```

with:

```kotlin
uiState.value = reportedNfcError(message)
```

Replace:

```kotlin
uiState.value = NfcSignInUiState.Error(result.message)
```

with:

```kotlin
uiState.value = reportedNfcError(result.message)
```

- [ ] **Step 7: Mark already-reported showError callers and add context to fallback callers**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanLocationActivationWorkflow.kt`.

For both branches that call `trackNfcException()` or `trackNfcFailure()` before `orderDelegate.showError(message)`, replace the call with:

```kotlin
orderDelegate.showError(
    message = message,
    source = "location_activation_order_detail",
    orderKey = orderKey,
    signInMode = signInMode,
    nfcDeviceId = tagId,
    buglyAlreadyReported = true,
    extras = mapOf(
        "hasLongitude" to longitude.isNotBlank(),
        "hasLatitude" to latitude.isNotBlank(),
    ),
)
```

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`.

Replace the scan location error callback:

```kotlin
onLocationError = { message -> orderDelegate.showError(message) },
```

with:

```kotlin
onLocationError = { message ->
    orderDelegate.showError(
        message = message,
        source = "scan_location_error",
        orderKey = orderKey,
        signInMode = signInMode,
    )
},
```

In `resumePendingPermissionScan()`, replace the location error branch:

```kotlin
orderDelegate.showError(locationResult.message)
return@launch
```

with:

```kotlin
orderDelegate.showError(
    message = locationResult.message,
    source = "resume_permission_scan_location_error",
    orderKey = scan.orderKey,
    signInMode = scan.signInMode,
    nfcDeviceId = scan.tagId,
)
return@launch
```

In `confirmLocationActivation()`, both branches already call `trackNfcException()` or `trackNfcFailure()`. Replace their `orderDelegate.showError(...)` calls with:

```kotlin
orderDelegate.showError(
    message = message,
    source = "bind_location",
    orderKey = data.orderKey,
    signInMode = data.signInMode,
    nfcDeviceId = data.tagId,
    buglyAlreadyReported = true,
    extras = mapOf(
        "hasLongitude" to data.longitude.isNotBlank(),
        "hasLatitude" to data.latitude.isNotBlank(),
    ),
)
```

and:

```kotlin
orderDelegate.showError(
    message = result.message,
    source = "bind_location",
    orderKey = data.orderKey,
    signInMode = data.signInMode,
    nfcDeviceId = data.tagId,
    buglyAlreadyReported = true,
    extras = mapOf(
        "hasLongitude" to data.longitude.isNotBlank(),
        "hasLatitude" to data.latitude.isNotBlank(),
    ),
)
```

- [ ] **Step 8: Confirm direct Error constructor call sites remain compatible**

The `buglyReported` property has a default value, so existing constructor calls remain valid. Confirm this existing call still compiles:

```kotlin
currentState = NfcSignInUiState.Error("位置信息错误")
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest'
```

Expected: PASS.

- [ ] **Step 9: Run targeted NFC tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.*'
```

Expected: PASS.

- [ ] **Step 10: Run compile verification**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit Task 2**

Run:

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegate.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlers.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowEndOrderExecutor.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanLocationActivationWorkflow.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowResultHandlersTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcOrderWorkflowDelegateTest.kt
git commit -m "fix(nfc): report user-visible errors to Bugly"
```

Expected: commit succeeds with Task 2 files staged.

---

## Final Verification

- [ ] Run all targeted NFC tests:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.*'
```

Expected: BUILD SUCCESSFUL.

- [ ] Run Kotlin compile:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] Inspect final diff:

```bash
git status --short --branch
git log -3 --oneline --decorate
```

Expected: working tree clean after the two implementation commits; latest commits are the Task 1 and Task 2 commits.
