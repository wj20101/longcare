# Type-C RFID Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an automatic Type-C RFID fallback for the NFC workflow so phones without built-in NFC can still produce a normalized `tagId` and reuse the current start-order and end-order flow.

**Architecture:** Keep one order workflow. Built-in NFC and the external Type-C reader both publish `AppEvent.TagScanned(tagId, source)` through `AppEventBus`; the NFC workflow listens only to that normalized event, while a separate `ReaderUiState` drives the external-reader connection UI. Preserve the existing `NfcIntentReceived` event for dashboard test helpers so the fallback work does not break unrelated tooling.

**Tech Stack:** Kotlin, Android USB host APIs, Hilt, Compose, SharedFlow, JUnit4, MockK, Robolectric, kotlinx-coroutines-test

---

## File Structure

### Modify

- `core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt`
  - Add the unified scan event types and the shared `ScanSource` enum.
  - Keep `NfcIntentReceived` for backward compatibility.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcManager.kt`
  - Publish `TagScanned` for built-in NFC while still emitting the legacy intent event.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
  - Add `ScanMode`, `ReaderUiState`, and small pure helpers such as `selectScanMode`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
  - Inject the external reader manager, expose `scanMode` and `readerUiState`, and own active scan-source startup/shutdown.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
  - Observe `TagScanned`, `ReaderConnectionChanged`, and `ReaderError`.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpers.kt`
  - Add pure helpers for `TagScanned` routing and reader-state reduction.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
  - Collect `scanMode` and `readerUiState` from the view model.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
  - Initialize the correct scan mode and start/stop only the active scan source.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
  - Feed the correct prompt copy into the body content.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`
  - Show external-reader disconnected and ready states.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`
  - Replace the fixed NFC idle hint with copy that depends on `ScanMode` and `ReaderUiState`.
- `app/src/main/res/values/strings.xml`
  - Add external-reader guidance strings.

### Create

- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
  - Normalize raw reader payloads into a `tagId`.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
  - Reader-manager interface used by the workflow.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
  - USB-host-backed default implementation that owns device lifecycle and publishes reader events.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderModule.kt`
  - Hilt binding for the default reader manager.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
  - Pure copy resolver for prompts, status labels, and idle hints.
- `app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcManagerTagEventTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`

---

### Task 1: Add shared scan contracts and tag normalization

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`
- Modify: `core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
- Test: `app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt`

- [ ] **Step 1: Write the failing parser test**

```kotlin
package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRfidTagParserTest {

    private val parser = ExternalRfidTagParser()

    @Test
    fun `normalize trims whitespace and uppercases tag ids`() {
        assertEquals("01AB9F", parser.normalize(" 01ab9f\r\n"))
    }

    @Test
    fun `normalize rejects blank payloads and non alphanumeric values`() {
        assertNull(parser.normalize("   "))
        assertNull(parser.normalize("01-AB"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"`
Expected: FAIL with unresolved references for `ExternalRfidTagParser`.

- [ ] **Step 3: Add the parser and new workflow contracts**

`app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt`

```kotlin
package com.ytone.longcare.common.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(" ", "")
            .uppercase()

        return normalized.takeIf {
            it.isNotBlank() && it.all(Char::isLetterOrDigit)
        }
    }
}
```

`core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt`

```kotlin
package com.ytone.longcare.common.event

import android.content.Intent
import com.ytone.longcare.model.AppVersionModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = _events.asSharedFlow()

    suspend fun send(event: AppEvent) {
        if (!_events.tryEmit(event)) {
            _events.emit(event)
        }
    }
}

enum class ScanSource {
    SYSTEM_NFC,
    EXTERNAL_RFID,
}

sealed class AppEvent {
    data class ForceLogout(val reason: String) : AppEvent()
    data class NfcIntentReceived(val intent: Intent) : AppEvent()
    data class TagScanned(val tagId: String, val source: ScanSource) : AppEvent()
    data class ReaderConnectionChanged(
        val connected: Boolean,
        val source: ScanSource = ScanSource.EXTERNAL_RFID,
    ) : AppEvent()
    data class ReaderError(
        val message: String,
        val source: ScanSource = ScanSource.EXTERNAL_RFID,
    ) : AppEvent()
    data class AppUpdate(val appVersionModel: AppVersionModel) : AppEvent()
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

enum class ScanMode {
    SYSTEM_NFC,
    EXTERNAL_RFID,
}

sealed class ReaderUiState {
    data object NotRequired : ReaderUiState()
    data object Disconnected : ReaderUiState()
    data object Ready : ReaderUiState()
    data object Reading : ReaderUiState()
    data class DeviceError(val message: String) : ReaderUiState()
}

internal fun selectScanMode(isNfcSupported: Boolean): ScanMode =
    if (isNfcSupported) ScanMode.SYSTEM_NFC else ScanMode.EXTERNAL_RFID

data class PendingNfcData(
    val orderKey: OrderKey,
    val signInMode: SignInMode,
    val endOderInfo: EndOderInfo?,
    val tagId: String,
    val longitude: String,
    val latitude: String,
)

sealed class NfcSignInUiState {
    data object Loading : NfcSignInUiState()
    data class Success(val endOrderSuccessData: EndOrderSuccessData? = null) : NfcSignInUiState()
    data class Error(
        val message: String,
        val occurrenceId: Long = System.nanoTime(),
    ) : NfcSignInUiState()
    data object Initial : NfcSignInUiState()
    data class ShowConfirmDialog(
        val message: String,
        val endOrderParams: EndOrderParams,
    ) : NfcSignInUiState()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"`
Expected: PASS with 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add \
  core/common/src/main/kotlin/com/ytone/longcare/common/event/AppEventBus.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParser.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/ExternalRfidTagParserTest.kt
git commit -m "feat: add NFC fallback scan contracts"
```

### Task 2: Publish unified scan events from built-in NFC and add the external reader seam

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderModule.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcManager.kt`
- Test: `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcManagerTagEventTest.kt`

- [ ] **Step 1: Write the failing built-in NFC event test**

```kotlin
package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NfcManagerTagEventTest {

    private val appEventBus = mockk<AppEventBus>(relaxed = true)
    private lateinit var activity: Activity
    private lateinit var nfcManager: NfcManager

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        nfcManager = NfcManager(appEventBus)

        mockkObject(NfcForegroundDispatchDelegate)
        mockkObject(NfcEnableDialogDelegate)
        mockkObject(NfcUtils)

        every { NfcForegroundDispatchDelegate.isResumed(any()) } returns false
        every { NfcEnableDialogDelegate.dismiss(any()) } returns null
    }

    @Test
    fun `handleNfcIntent emits legacy intent event and unified tag event`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk {
            every { id } returns byteArrayOf(0x01, 0x0A)
        }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"

        nfcManager.enableNfcForActivity(activity)
        nfcManager.handleNfcIntent(activity, Intent("android.nfc.action.TAG_DISCOVERED"))
        advanceUntilIdle()

        coVerify { appEventBus.send(match<AppEvent.NfcIntentReceived> { true }) }
        coVerify { appEventBus.send(AppEvent.TagScanned("010A", ScanSource.SYSTEM_NFC)) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.NfcManagerTagEventTest"`
Expected: FAIL because `TagScanned` is not yet emitted from `NfcManager`.

- [ ] **Step 3: Implement the event publisher and reader seam**

`app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt`

```kotlin
package com.ytone.longcare.common.utils

import android.app.Activity

interface ExternalRfidReaderManager {
    fun start(activity: Activity)
    fun stop(activity: Activity)
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt`

```kotlin
package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbExternalRfidReaderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appEventBus: AppEventBus,
    private val parser: ExternalRfidTagParser,
) : ExternalRfidReaderManager {

    private val permissionAction = "${context.packageName}.USB_PERMISSION_RFID"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private var receiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> publishConnectionState(true)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> publishConnectionState(false)
                permissionAction -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (!granted) publishReaderError("未授予读卡器访问权限")
                }
            }
        }
    }

    override fun start(activity: Activity) {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(permissionAction)
            }
            context.registerReceiver(usbReceiver, filter)
            receiverRegistered = true
        }
        publishConnectionState(usbManager.deviceList.isNotEmpty())
    }

    override fun stop(activity: Activity) {
        if (receiverRegistered) {
            context.unregisterReceiver(usbReceiver)
            receiverRegistered = false
        }
    }

    private fun publishConnectionState(connected: Boolean) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderConnectionChanged(connected, ScanSource.EXTERNAL_RFID))
        }
    }

    internal fun publishRawPayload(rawPayload: String) {
        val tagId = parser.normalize(rawPayload) ?: return
        scope.launch {
            appEventBus.send(AppEvent.TagScanned(tagId, ScanSource.EXTERNAL_RFID))
        }
    }

    internal fun publishReaderError(message: String) {
        scope.launch {
            appEventBus.send(AppEvent.ReaderError(message, ScanSource.EXTERNAL_RFID))
        }
    }
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderModule.kt`

```kotlin
package com.ytone.longcare.common.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExternalRfidReaderModule {
    @Binds
    @Singleton
    abstract fun bindExternalRfidReaderManager(
        impl: UsbExternalRfidReaderManager,
    ): ExternalRfidReaderManager
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/NfcManager.kt`

```kotlin
private fun handleBuiltInTag(intent: Intent) {
    val tag = NfcUtils.getTagFromIntent(intent) ?: return
    val tagId = NfcUtils.bytesToHexString(tag.id)
    if (tagId.isBlank()) return

    currentActivity?.takeIf { isNfcEnabled }?.let { activity ->
        if (activity is LifecycleOwner) {
            activity.lifecycleScope.launch {
                appEventBus.send(AppEvent.TagScanned(tagId, ScanSource.SYSTEM_NFC))
            }
        }
    }
}

fun handleNfcIntent(activity: Activity, intent: Intent) {
    if (currentActivity != activity || !isNfcEnabled) return

    activity.intent = intent
    if (activity is LifecycleOwner) {
        activity.lifecycleScope.launch {
            appEventBus.send(AppEvent.NfcIntentReceived(intent))
        }
    }
    handleBuiltInTag(intent)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.NfcManagerTagEventTest"`
Expected: PASS with the legacy event and the new unified event both verified.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbExternalRfidReaderManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ExternalRfidReaderModule.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/NfcManager.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/NfcManagerTagEventTest.kt
git commit -m "feat: publish unified tag scan events"
```

### Task 3: Route unified events through the workflow and keep reader state separate

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpers.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`
- Test: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt`

- [ ] **Step 1: Write the failing workflow helper tests**

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcScanWorkflowHelpersTest {

    @Test
    fun `handleTagScanned routes start order with normalized tag id`() = runTest {
        var started: Triple<String, String, String>? = null

        handleTagScanned(
            event = AppEvent.TagScanned("ABC123", ScanSource.EXTERNAL_RFID),
            currentState = NfcSignInUiState.Initial,
            signInMode = SignInMode.START_ORDER,
            endOderInfo = null,
            onLocationRequest = { LocationRequestResult.Coordinates("121.47", "31.23") },
            onLocationError = { error("unexpected location error: $it") },
            onStartOrder = { tagId, longitude, latitude ->
                started = Triple(tagId, longitude, latitude)
            },
            onEndOrder = { _, _, _, _ -> error("unexpected end order") },
        )

        assertEquals(Triple("ABC123", "121.47", "31.23"), started)
    }

    @Test
    fun `reduceReaderUiState keeps business and device state separate`() {
        assertTrue(
            reduceReaderUiState(
                currentMode = ScanMode.EXTERNAL_RFID,
                event = AppEvent.ReaderConnectionChanged(connected = false),
                currentReaderState = ReaderUiState.Ready,
            ) is ReaderUiState.Disconnected,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"`
Expected: FAIL because `handleTagScanned` and `reduceReaderUiState` do not exist yet.

- [ ] **Step 3: Implement the helper functions and wire the view model**

`app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpers.kt`

```kotlin
package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

internal suspend fun handleTagScanned(
    event: AppEvent.TagScanned,
    currentState: NfcSignInUiState,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    onLocationRequest: suspend () -> LocationRequestResult,
    onLocationError: (String) -> Unit,
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit,
) {
    if (currentState is NfcSignInUiState.Success) return

    val locationResult = onLocationRequest()
    val (longitude, latitude) = when (locationResult) {
        is LocationRequestResult.Coordinates -> locationResult.longitude to locationResult.latitude
        is LocationRequestResult.Error -> {
            onLocationError(locationResult.message)
            return
        }
    }

    executeSignInModeAction(
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        tagId = event.tagId,
        longitude = longitude,
        latitude = latitude,
        onStartOrder = onStartOrder,
        onEndOrder = onEndOrder,
    )
}

internal fun reduceReaderUiState(
    currentMode: ScanMode,
    event: AppEvent,
    currentReaderState: ReaderUiState,
): ReaderUiState = when {
    currentMode == ScanMode.SYSTEM_NFC -> ReaderUiState.NotRequired
    event is AppEvent.ReaderConnectionChanged && event.connected -> ReaderUiState.Ready
    event is AppEvent.ReaderConnectionChanged && !event.connected -> ReaderUiState.Disconnected
    event is AppEvent.ReaderError -> ReaderUiState.DeviceError(event.message)
    else -> currentReaderState
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt`

```kotlin
internal class NfcScanWorkflowDelegate(
    private val appEventBus: AppEventBus,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val orderRepository: OrderRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<NfcSignInUiState>,
    private val readerUiState: MutableStateFlow<ReaderUiState>,
    private val scanMode: MutableStateFlow<ScanMode>,
    private val pendingNfcData: MutableStateFlow<PendingNfcData?>,
    private val orderDelegate: NfcOrderWorkflowDelegate,
) {
    fun observeScanEvents(
        orderKey: OrderKey,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> LocationRequestResult,
    ) {
        nfcEventJob?.cancel()
        nfcEventJob = scope.launch {
            appEventBus.events.collect { event ->
                when (event) {
                    is AppEvent.TagScanned -> handleTagScanned(
                        event = event,
                        currentState = uiState.value,
                        signInMode = signInMode,
                        endOderInfo = endOderInfo,
                        onLocationRequest = onLocationRequest,
                        onLocationError = { message -> orderDelegate.showError(message) },
                        onStartOrder = { tagId, longitude, latitude ->
                            checkUserLocationAndProceed(
                                unifiedOrderRepository = unifiedOrderRepository,
                                orderKey = orderKey,
                                signInMode = signInMode,
                                endOderInfo = endOderInfo,
                                tagId = tagId,
                                longitude = longitude,
                                latitude = latitude,
                                pendingNfcData = pendingNfcData,
                                scope = scope,
                                orderDelegate = orderDelegate,
                            )
                        },
                        onEndOrder = { tagId, longitude, latitude, info ->
                            orderDelegate.endOrder(
                                orderKey = orderKey,
                                nfcDeviceId = tagId,
                                projectIdList = info.projectIdList,
                                beginImgList = info.beginImgList,
                                centerImgList = info.centerImgList,
                                endImageList = info.endImgList,
                                longitude = longitude,
                                latitude = latitude,
                                endType = info.endType,
                            )
                        },
                    )
                    is AppEvent.ReaderConnectionChanged,
                    is AppEvent.ReaderError -> {
                        readerUiState.value = reduceReaderUiState(scanMode.value, event, readerUiState.value)
                    }
                    else -> Unit
                }
            }
        }
    }
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`

```kotlin
class NfcWorkflowViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val appEventBus: AppEventBus,
    private val nfcManager: NfcManager,
    private val externalRfidReaderManager: ExternalRfidReaderManager,
    private val locationFacade: LocationFacade,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val countdownNotificationManager: CountdownNotificationManager,
) : ViewModel() {
private val _scanMode = MutableStateFlow(selectScanMode(activityAndLocationDelegate.isNfcSupported()))
val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

private val _readerUiState = MutableStateFlow<ReaderUiState>(
    if (_scanMode.value == ScanMode.SYSTEM_NFC) ReaderUiState.NotRequired else ReaderUiState.Disconnected,
)
val readerUiState: StateFlow<ReaderUiState> = _readerUiState.asStateFlow()

private val scanDelegate = NfcScanWorkflowDelegate(
    appEventBus = appEventBus,
    unifiedOrderRepository = unifiedOrderRepository,
    orderRepository = orderRepository,
    scope = viewModelScope,
    uiState = _uiState,
    readerUiState = _readerUiState,
    scanMode = _scanMode,
    pendingNfcData = _pendingNfcData,
    orderDelegate = orderDelegate,
)

fun startActiveScanSource(activity: Activity) {
    when (_scanMode.value) {
        ScanMode.SYSTEM_NFC -> activityAndLocationDelegate.enableNfcForActivity(activity)
        ScanMode.EXTERNAL_RFID -> externalRfidReaderManager.start(activity)
    }
}

fun stopActiveScanSource(activity: Activity) {
    when (_scanMode.value) {
        ScanMode.SYSTEM_NFC -> activityAndLocationDelegate.disableNfcForActivity(activity)
        ScanMode.EXTERNAL_RFID -> externalRfidReaderManager.stop(activity)
    }
}

fun observeScanEvents(
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    onLocationRequest: suspend () -> LocationRequestResult,
) = scanDelegate.observeScanEvents(orderKey, signInMode, endOderInfo, onLocationRequest)
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt`

```kotlin
LaunchedEffect(activity) {
    activity?.let { nfcViewModel.startActiveScanSource(it) }
}

LaunchedEffect(orderKey, signInMode) {
    nfcViewModel.observeScanEvents(
        orderKey = orderKey,
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        onLocationRequest = { onLocationRequest() },
    )
}

DisposableEffect(activity) {
    onDispose {
        activity?.let { nfcViewModel.stopActiveScanSource(it) }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"`
Expected: PASS with helper tests green.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowDelegate.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpers.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowEffects.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcScanWorkflowHelpersTest.kt
git commit -m "feat: route NFC workflow through unified scan events"
```

### Task 4: Update UI copy and screen rendering for external-reader mode

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`

- [ ] **Step 1: Write the failing UI copy test**

```kotlin
package com.ytone.longcare.features.nfc.ui

import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowUiCopyTest {

    @Test
    fun `external disconnected copy instructs the user to connect a type c reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Disconnected,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT, copy.bottomHintKey)
    }

    @Test
    fun `external ready copy tells the user to scan on the reader`() {
        val copy = resolveNfcWorkflowIdleCopy(
            scanMode = ScanMode.EXTERNAL_RFID,
            readerUiState = ReaderUiState.Ready,
        )

        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT, copy.promptKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_STATUS, copy.statusKey)
        assertEquals(NfcWorkflowCopyKey.EXTERNAL_READY_HINT, copy.bottomHintKey)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"`
Expected: FAIL because the copy resolver does not exist yet.

- [ ] **Step 3: Add the copy resolver, strings, and UI wiring**

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt`

```kotlin
package com.ytone.longcare.features.nfc.ui

import com.ytone.longcare.R
import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode

enum class NfcWorkflowCopyKey {
    BUILT_IN_PROMPT,
    BUILT_IN_HINT,
    EXTERNAL_DISCONNECTED_PROMPT,
    EXTERNAL_DISCONNECTED_STATUS,
    EXTERNAL_DISCONNECTED_HINT,
    EXTERNAL_READY_PROMPT,
    EXTERNAL_READY_STATUS,
    EXTERNAL_READY_HINT,
}

data class NfcWorkflowIdleCopy(
    val promptKey: NfcWorkflowCopyKey,
    val statusKey: NfcWorkflowCopyKey?,
    val bottomHintKey: NfcWorkflowCopyKey,
)

internal fun resolveNfcWorkflowIdleCopy(
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
): NfcWorkflowIdleCopy = when (scanMode) {
    ScanMode.SYSTEM_NFC -> NfcWorkflowIdleCopy(
        promptKey = NfcWorkflowCopyKey.BUILT_IN_PROMPT,
        statusKey = null,
        bottomHintKey = NfcWorkflowCopyKey.BUILT_IN_HINT,
    )
    ScanMode.EXTERNAL_RFID -> when (readerUiState) {
        ReaderUiState.Ready,
        ReaderUiState.Reading -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_READY_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_READY_HINT,
        )
        ReaderUiState.DeviceError,
        ReaderUiState.Disconnected,
        ReaderUiState.NotRequired -> NfcWorkflowIdleCopy(
            promptKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT,
            statusKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS,
            bottomHintKey = NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT,
        )
    }
}

internal fun resolveCopyRes(key: NfcWorkflowCopyKey): Int = when (key) {
    NfcWorkflowCopyKey.BUILT_IN_PROMPT -> R.string.nfc_sign_in_prompt
    NfcWorkflowCopyKey.BUILT_IN_HINT -> R.string.nfc_sign_in_idle_hint
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_PROMPT -> R.string.nfc_external_reader_prompt
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_STATUS -> R.string.nfc_external_reader_disconnected
    NfcWorkflowCopyKey.EXTERNAL_DISCONNECTED_HINT -> R.string.nfc_external_reader_disconnected_hint
    NfcWorkflowCopyKey.EXTERNAL_READY_PROMPT -> R.string.nfc_external_reader_ready_prompt
    NfcWorkflowCopyKey.EXTERNAL_READY_STATUS -> R.string.nfc_external_reader_ready
    NfcWorkflowCopyKey.EXTERNAL_READY_HINT -> R.string.nfc_external_reader_ready_hint
}
```

`app/src/main/res/values/strings.xml`

```xml
<string name="nfc_sign_in_idle_hint">请将NFC设备靠近手机背面</string>
<string name="nfc_external_reader_prompt">当前设备不支持NFC，请连接Type-C读卡器</string>
<string name="nfc_external_reader_disconnected">未连接读卡器</string>
<string name="nfc_external_reader_disconnected_hint">请插入读卡器后重试</string>
<string name="nfc_external_reader_ready_prompt">读卡器已连接，请将卡片靠近读卡器</string>
<string name="nfc_external_reader_ready">已连接，等待刷卡</string>
<string name="nfc_external_reader_ready_hint">识别成功后将自动继续签到流程</string>
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`

```kotlin
val scanMode by nfcViewModel.scanMode.collectAsStateWithLifecycle()
val readerUiState by nfcViewModel.readerUiState.collectAsStateWithLifecycle()

NfcWorkflowBodyContent(
    paddingValues = paddingValues,
    orderKey = orderKey,
    signInMode = signInMode,
    endOderInfo = endOderInfo,
    nfcViewModel = nfcViewModel,
    signInState = signInState,
    scanMode = scanMode,
    readerUiState = readerUiState,
)

NfcWorkflowBottomBar(
    signInState = signInState,
    signInMode = signInMode,
    scanMode = scanMode,
    readerUiState = readerUiState,
    onSuccessClick = singleClick {
        handleNfcSuccessAction(
            signInMode = signInMode,
            orderKey = orderKey,
            endOderInfo = endOderInfo,
            uiState = uiState,
            nfcViewModel = nfcViewModel,
            locationTrackingViewModel = locationTrackingViewModel,
            actions = actions,
            startTrackingWithPermission = locationHandlers.startTrackingWithPermission,
        )
    },
    onRetryClick = { nfcViewModel.resetState() },
)
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt`

```kotlin
@Composable
internal fun NfcWorkflowBodyContent(
    paddingValues: PaddingValues,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    nfcViewModel: NfcWorkflowViewModel,
    signInState: SignInState,
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
) {
    val idleCopy = resolveNfcWorkflowIdleCopy(scanMode, readerUiState)

    Text(
        text = stringResource(resolveCopyRes(idleCopy.promptKey)),
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.9f),
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
    )

    SignInContentCard(
        signInState = signInState,
        statusOverrideRes = idleCopy.statusKey?.let(::resolveCopyRes),
    )
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`

```kotlin
@Composable
internal fun SignInContentCard(
    signInState: SignInState,
    statusOverrideRes: Int? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(325f / 260f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (signInState) {
                SignInState.SUCCESS -> StatusDisplay(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.nfc_sign_in_status_success),
                    iconColor = Color(0xFF34C759),
                )
                SignInState.FAILURE -> StatusDisplay(
                    icon = Icons.Default.Error,
                    text = stringResource(R.string.nfc_sign_in_status_failure),
                    iconColor = Color.Red,
                )
                SignInState.IDLE -> {
                    val statusRes = statusOverrideRes
                    if (statusRes != null) {
                        Text(
                            text = stringResource(statusRes),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Image(
                painter = painterResource(id = R.drawable.nfc_interaction_diagram),
                contentDescription = stringResource(R.string.nfc_sign_in_diagram_description),
                modifier = Modifier
                    .padding(start = 48.dp)
                    .size(170.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`

```kotlin
@Composable
internal fun NfcWorkflowBottomBar(
    signInState: SignInState,
    signInMode: SignInMode,
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
    onSuccessClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    val idleCopy = resolveNfcWorkflowIdleCopy(scanMode, readerUiState)

    when (signInState) {
        SignInState.SUCCESS -> {
            val buttonText = when (signInMode) {
                SignInMode.START_ORDER -> stringResource(R.string.common_next_step)
                SignInMode.END_ORDER -> stringResource(R.string.nfc_sign_out_complete_service)
            }
            ActionButton(text = buttonText, onClick = onSuccessClick)
        }
        SignInState.FAILURE -> {
            ActionButton(
                text = stringResource(R.string.nfc_sign_in_retry),
                onClick = onRetryClick,
            )
        }
        SignInState.IDLE -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f),
                ),
            ) {
                Text(
                    text = stringResource(resolveCopyRes(idleCopy.bottomHintKey)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Black.copy(alpha = 0.7f),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"`
Expected: PASS with disconnected and ready copy cases green.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopy.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowLayoutSections.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt
git commit -m "feat: show external RFID reader states in NFC workflow"
```

### Task 5: Run regression checks and complete the manual device checklist

**Files:**
- Modify: `docs/superpowers/plans/2026-04-05-type-c-rfid-fallback.md` (check off completed steps during execution only)

- [ ] **Step 1: Run the focused unit test suite**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.ExternalRfidTagParserTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.NfcManagerTagEventTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.vm.NfcScanWorkflowHelpersTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"
```

Expected: PASS for all four test classes.

- [ ] **Step 2: Run module-level safety checks**

Run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` for compile, and no new lint errors introduced by the fallback work.

- [ ] **Step 3: Run the manual built-in NFC regression checklist**

Checklist:

```text
1. Open NFC workflow on a phone with built-in NFC support.
2. Verify the screen still shows the original NFC prompt.
3. Scan a real tag and confirm the start-order path still works.
4. Repeat on end-order and confirm the success navigation is unchanged.
5. Trigger a known business failure and verify the existing error dialog and retry button still work.
```

Expected: The workflow behaves exactly as before on supported phones.

- [ ] **Step 4: Run the manual external-reader checklist**

Checklist:

```text
1. Open NFC workflow on a phone without built-in NFC support.
2. Verify the screen automatically shows the Type-C reader disconnected state.
3. Insert the reader and verify the status changes to "已连接，等待刷卡".
4. Present a compatible tag through the reader and verify the workflow continues automatically.
5. Remove the reader and verify the screen returns to the disconnected state.
6. Deny USB permission if prompted and verify the screen shows a device-level error instead of a business failure screen.
```

Expected: The screen distinguishes disconnected vs ready, and valid scans reuse the order workflow.

- [ ] **Step 5: Commit the finished feature branch state**

```bash
git status --short
# Verify only intended fallback files changed.
git add app/src/main core/common/src/main app/src/test app/src/main/res/values/strings.xml
git commit -m "feat: add Type-C RFID fallback for NFC workflow"
```

---

## Spec Coverage Check

- Scan mode selection: Task 1 adds `ScanMode`; Task 3 wires startup and shutdown.
- Unified scan event layer: Task 1 adds `TagScanned`; Task 2 publishes it; Task 3 consumes it.
- External reader integration layer: Task 2 adds the manager, parser, and Hilt binding.
- Workflow consumption path: Task 3 routes `TagScanned` through the existing order workflow.
- Separate device state from business state: Task 1 adds `ReaderUiState`; Task 3 updates it independently; Task 4 renders it.
- External-reader UX: Task 4 adds the disconnected and ready copy and hooks it into the existing screen.
- Error layering: Task 3 keeps device events in `ReaderUiState`; business failures remain in `NfcSignInUiState.Error`.
- Compatibility seam: Task 2 adds `ExternalRfidTagParser` and `ExternalRfidReaderManager` as swappable boundaries.
- Verification strategy: Task 5 covers focused tests, compile/lint, and both manual checklists.

## Placeholder Scan

- No `TBD`, `TODO`, or deferred implementation notes remain.
- Every code-changing task includes concrete file paths, code blocks, and runnable commands.
- The plan preserves `NfcIntentReceived` explicitly so the dashboard NFC test helper is not orphaned.

## Type Consistency Check

- Shared event source is always `ScanSource`.
- Screen mode is always `ScanMode`.
- Device UI state is always `ReaderUiState`.
- Business routing helper is always `handleTagScanned`.
- External-manager interface is always `ExternalRfidReaderManager` with `UsbExternalRfidReaderManager` as the default implementation.
