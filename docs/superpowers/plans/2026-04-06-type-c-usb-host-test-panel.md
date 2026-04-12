# Type-C USB Host Test Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a diagnostic Type-C USB Host test panel to the existing NFC test screen so the team can inspect USB device detection, permission state, interface and endpoint details, raw payload attempts, parser results, and live attach/detach changes without emitting production scan events.

**Architecture:** Keep the existing `NfcTestScreen` route and add a second panel driven by a dedicated test-only `TypeCRfidTestViewModel`. A generic `UsbHostProbeManager` performs USB enumeration, permission requests, attach/detach observation, interface inspection, and conservative read attempts; the panel renders `UsbProbeUiState`, device summary, raw payload text and hex, and parser results. Automatic USB device observation should reuse the same refresh path as the manual refresh button instead of introducing a second state machine.

**Tech Stack:** Kotlin, Compose, Hilt, Android USB Host APIs (`UsbManager`, `UsbDeviceConnection`, `UsbEndpoint`, `BroadcastReceiver`), JUnit4, MockK, kotlinx-coroutines-test

---

## File Structure

### Modify

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
  - Collect the new test view model, render the Type-C panel, and bind observation start/stop to the screen lifecycle.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
  - Keep the dashboard scrollable and render both the existing NFC card and the USB Host panel.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`
  - Render richer diagnostics for permission, class info, and endpoint summary.
- `app/src/main/res/values/strings.xml`
  - Add any missing strings for Type-C USB Host testing copy.

### Create

- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt`
  - Test-only state holder for USB probing and auto-refresh on attach/detach.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt`
  - `UsbProbeUiState`, panel state, and helpers.
- `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`
  - Composable panel for connection state, device info, raw payload, parser result, and actions.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`
  - Generic USB Host probe manager, including attach/detach observation APIs.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt`
  - Low-level probe result models returned by the manager.
- `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt`
  - Hilt binding for the probe manager implementation.
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt`

---

### Task 1: Add USB Host probe contracts and low-level probe manager seam

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`
- Test: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt`

- [ ] **Step 1: Write the failing contracts test**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Test

class TypeCRfidTestContractsTest {

    @Test
    fun `raw payload hex is uppercase and space separated`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.Ready,
            rawPayload = byteArrayOf(0x01, 0x0A, 0x2F),
        )

        assertEquals("01 0A 2F", state.rawPayloadHex)
    }

    @Test
    fun `parsed tag id falls back to not parsed when parser returns null`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.ReadFailed("timeout"),
            parsedTagId = null,
        )

        assertEquals("未解析出卡号", state.parsedTagDisplay)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestContractsTest"`
Expected: FAIL with unresolved references for `TypeCRfidPanelState` and `UsbProbeUiState`.

- [ ] **Step 3: Create contracts and probe models**

`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt`

```kotlin
package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.common.utils.UsbDeviceSummary

sealed class UsbProbeUiState {
    data object Idle : UsbProbeUiState()
    data object NoDevice : UsbProbeUiState()
    data object DeviceDetected : UsbProbeUiState()
    data object PermissionDenied : UsbProbeUiState()
    data object Ready : UsbProbeUiState()
    data object Reading : UsbProbeUiState()
    data class ReadFailed(val message: String) : UsbProbeUiState()
}

data class TypeCRfidPanelState(
    val probeState: UsbProbeUiState = UsbProbeUiState.Idle,
    val deviceSummary: UsbDeviceSummary? = null,
    val rawPayload: ByteArray? = null,
    val rawPayloadText: String? = null,
    val parsedTagId: String? = null,
    val lastUpdatedAt: String? = null,
) {
    val rawPayloadHex: String = rawPayload
        ?.joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        .orEmpty()

    val parsedTagDisplay: String = parsedTagId ?: "未解析出卡号"
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt`

```kotlin
package com.ytone.longcare.common.utils

data class UsbEndpointSummary(
    val address: Int,
    val direction: Int,
    val type: Int,
    val maxPacketSize: Int,
)

data class UsbDeviceSummary(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val endpoints: List<UsbEndpointSummary>,
)

sealed class UsbHostProbeResult {
    data object NoDevice : UsbHostProbeResult()
    data class DeviceFound(
        val summary: UsbDeviceSummary,
        val hasPermission: Boolean,
    ) : UsbHostProbeResult()
    data class ReadSuccess(
        val summary: UsbDeviceSummary,
        val payload: ByteArray,
    ) : UsbHostProbeResult()
    data class ReadFailure(
        val summary: UsbDeviceSummary?,
        val message: String,
    ) : UsbHostProbeResult()
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`

```kotlin
package com.ytone.longcare.common.utils

import android.app.Activity
import kotlinx.coroutines.flow.Flow

sealed class UsbHostDeviceEvent {
    data object Attached : UsbHostDeviceEvent()
    data object Detached : UsbHostDeviceEvent()
}

interface UsbHostProbeManager {
    fun refresh(): UsbHostProbeResult
    fun requestPermission(activity: Activity): UsbHostProbeResult
    fun attemptRead(activity: Activity): UsbHostProbeResult
    fun observeDeviceChanges(): Flow<UsbHostDeviceEvent>
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestContractsTest"`
Expected: PASS with 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContracts.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModels.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestContractsTest.kt
git commit -m "feat: add Type-C USB host probe contracts"
```

### Task 2: Implement the generic USB Host probe manager with attach/detach observation

**Files:**
- Modify/Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt`
- Test: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt`

- [ ] **Step 1: Write the failing manager-consumer test through the ViewModel seam**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostDeviceEvent
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeCRfidTestViewModelTest {

    @Test
    fun `refresh updates state to no device when probe manager finds nothing`() {
        val probeManager = mockk<UsbHostProbeManager>()
        every { probeManager.refresh() } returns UsbHostProbeResult.NoDevice
        every { probeManager.observeDeviceChanges() } returns MutableSharedFlow()

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
        )

        viewModel.refreshDevices()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.NoDevice)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestViewModelTest"`
Expected: FAIL because the observation path is not implemented yet.

- [ ] **Step 3: Implement the generic USB Host probe manager and Hilt binding**

`app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt`

```kotlin
package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class DefaultUsbHostProbeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsbHostProbeManager {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun refresh(): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        return UsbHostProbeResult.DeviceFound(
            summary = device.toSummary(),
            hasPermission = usbManager.hasPermission(device),
        )
    }

    override fun requestPermission(activity: Activity): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        return UsbHostProbeResult.DeviceFound(
            summary = device.toSummary(),
            hasPermission = usbManager.hasPermission(device),
        )
    }

    override fun attemptRead(activity: Activity): UsbHostProbeResult {
        val device = usbManager.deviceList.values.firstOrNull() ?: return UsbHostProbeResult.NoDevice
        val summary = device.toSummary()
        if (!usbManager.hasPermission(device)) {
            return UsbHostProbeResult.ReadFailure(summary, "USB权限未授予")
        }

        val connection = usbManager.openDevice(device)
            ?: return UsbHostProbeResult.ReadFailure(summary, "无法打开USB设备")

        var claimedInterface: android.hardware.usb.UsbInterface? = null
        try {
            val candidateInterfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
            val candidateEndpoints = candidateInterfaces.flatMap { usbInterface ->
                (0 until usbInterface.endpointCount).map { endpointIndex ->
                    usbInterface to usbInterface.getEndpoint(endpointIndex)
                }
            }

            val readableEndpoint = candidateEndpoints.firstOrNull { (_, endpoint) ->
                endpoint.direction == UsbConstants.USB_DIR_IN &&
                    (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                        endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT)
            } ?: return UsbHostProbeResult.ReadFailure(summary, "未找到可读Endpoint")

            claimedInterface = readableEndpoint.first
            if (!connection.claimInterface(claimedInterface, false)) {
                return UsbHostProbeResult.ReadFailure(summary, "无法声明USB Interface")
            }

            val endpoint = readableEndpoint.second
            val buffer = ByteArray(endpoint.maxPacketSize.coerceAtLeast(64))
            val length = connection.bulkTransfer(endpoint, buffer, buffer.size, 300)
            if (length <= 0) {
                return UsbHostProbeResult.ReadFailure(summary, "未读取到原始数据")
            }

            return UsbHostProbeResult.ReadSuccess(summary, buffer.copyOf(length))
        } finally {
            claimedInterface?.let(connection::releaseInterface)
            connection.close()
        }
    }

    override fun observeDeviceChanges(): Flow<UsbHostDeviceEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> trySend(UsbHostDeviceEvent.Attached)
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> trySend(UsbHostDeviceEvent.Detached)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun UsbDevice.toSummary() = UsbDeviceSummary(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        deviceClass = deviceClass,
        deviceSubclass = deviceSubclass,
        deviceProtocol = deviceProtocol,
        interfaceCount = interfaceCount,
        endpoints = (0 until interfaceCount).flatMap { index ->
            val usbInterface = getInterface(index)
            (0 until usbInterface.endpointCount).map { endpointIndex ->
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                UsbEndpointSummary(
                    address = endpoint.address,
                    direction = endpoint.direction,
                    type = endpoint.type,
                    maxPacketSize = endpoint.maxPacketSize,
                )
            }
        },
    )
}
```

`app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt`

```kotlin
package com.ytone.longcare.common.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsbHostProbeModule {
    @Binds
    @Singleton
    abstract fun bindUsbHostProbeManager(
        impl: DefaultUsbHostProbeManager,
    ): UsbHostProbeManager
}
```

- [ ] **Step 4: Run test to verify it still compiles up to the ViewModel seam**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestViewModelTest"`
Expected: still FAIL until the ViewModel observation logic is added in Task 3.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/UsbHostProbeModule.kt
git commit -m "feat: add generic USB host probe manager"
```

### Task 3: Add the Type-C test ViewModel with auto-refresh on attach/detach

**Files:**
- Create/Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt`

- [ ] **Step 1: Expand the failing ViewModel tests**

```kotlin
package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostDeviceEvent
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import com.ytone.longcare.common.utils.UsbDeviceSummary
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypeCRfidTestViewModelTest {

    private val fixedNow = "12:34:56"

    private fun sampleSummary() = UsbDeviceSummary(
        deviceName = "reader-1",
        vendorId = 1234,
        productId = 5678,
        deviceClass = 0,
        deviceSubclass = 0,
        deviceProtocol = 0,
        interfaceCount = 1,
        endpoints = emptyList(),
    )

    @Test
    fun `refresh updates state to no device when probe manager finds nothing`() {
        val probeManager = mockk<UsbHostProbeManager>()
        every { probeManager.refresh() } returns UsbHostProbeResult.NoDevice
        every { probeManager.observeDeviceChanges() } returns MutableSharedFlow()

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.refreshDevices()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.NoDevice)
    }

    @Test
    fun `device attach event reuses refresh path`() = runTest {
        val probeManager = mockk<UsbHostProbeManager>()
        val deviceEvents = MutableSharedFlow<UsbHostDeviceEvent>()
        every { probeManager.observeDeviceChanges() } returns deviceEvents
        every { probeManager.refresh() } returns UsbHostProbeResult.DeviceFound(
            summary = sampleSummary(),
            hasPermission = true,
        )

        val viewModel = TypeCRfidTestViewModel(
            probeManager = probeManager,
            parser = mockk(relaxed = true),
            nowProvider = { fixedNow },
        )

        viewModel.startObserving()
        deviceEvents.emit(UsbHostDeviceEvent.Attached)
        advanceUntilIdle()

        assertTrue(viewModel.panelState.value.probeState is UsbProbeUiState.Ready)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestViewModelTest"`
Expected: FAIL until observation logic is implemented.

- [ ] **Step 3: Implement observation-aware ViewModel**

`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt`

```kotlin
package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostDeviceEvent
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TypeCRfidTestViewModel @Inject constructor(
    private val probeManager: UsbHostProbeManager,
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var observeJob: Job? = null

    internal constructor(
        probeManager: UsbHostProbeManager,
        parser: ExternalRfidTagParser,
        nowProvider: () -> String,
    ) : this(probeManager, parser) {
        this.nowProvider = nowProvider
    }

    private val _panelState = MutableStateFlow(TypeCRfidPanelState())
    val panelState: StateFlow<TypeCRfidPanelState> = _panelState.asStateFlow()

    fun startObserving() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            probeManager.observeDeviceChanges().collect { event ->
                when (event) {
                    UsbHostDeviceEvent.Attached,
                    UsbHostDeviceEvent.Detached,
                    -> refreshDevices()
                }
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun refreshDevices() {
        applyProbeResult(probeManager.refresh())
    }

    fun requestPermission(activity: Activity) {
        applyProbeResult(probeManager.requestPermission(activity))
    }

    fun attemptRead(activity: Activity) {
        _panelState.value = _panelState.value.copy(probeState = UsbProbeUiState.Reading)
        applyProbeResult(probeManager.attemptRead(activity))
    }

    private fun applyProbeResult(result: UsbHostProbeResult) {
        _panelState.value = when (result) {
            UsbHostProbeResult.NoDevice -> TypeCRfidPanelState(
                probeState = UsbProbeUiState.NoDevice,
            )
            is UsbHostProbeResult.DeviceFound -> TypeCRfidPanelState(
                probeState = if (result.hasPermission) UsbProbeUiState.Ready else UsbProbeUiState.DeviceDetected,
                deviceSummary = result.summary,
                lastUpdatedAt = nowProvider(),
            )
            is UsbHostProbeResult.ReadFailure -> TypeCRfidPanelState(
                probeState = if (result.message == "USB权限未授予") {
                    UsbProbeUiState.PermissionDenied
                } else {
                    UsbProbeUiState.ReadFailed(result.message)
                },
                deviceSummary = result.summary,
                lastUpdatedAt = nowProvider(),
            )
            is UsbHostProbeResult.ReadSuccess -> {
                val text = result.payload.toString(Charsets.UTF_8)
                TypeCRfidPanelState(
                    probeState = UsbProbeUiState.Ready,
                    deviceSummary = result.summary,
                    rawPayload = result.payload,
                    rawPayloadText = text,
                    parsedTagId = parser.normalize(text),
                    lastUpdatedAt = nowProvider(),
                )
            }
        }
    }

    private fun nowString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onCleared() {
        stopObserving()
        super.onCleared()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModel.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/vm/TypeCRfidTestViewModelTest.kt
git commit -m "feat: add Type-C RFID test view model"
```

### Task 4: Render the Type-C USB Host test panel and lifecycle observation inside NfcTestScreen

**Files:**
- Create/Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt`

- [ ] **Step 1: Extend the failing panel state test**

```kotlin
package com.ytone.longcare.features.nfctest.ui

import com.ytone.longcare.common.utils.UsbDeviceSummary
import com.ytone.longcare.common.utils.UsbEndpointSummary
import com.ytone.longcare.features.nfctest.vm.TypeCRfidPanelState
import com.ytone.longcare.features.nfctest.vm.UsbProbeUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeCRfidTestPanelStateTest {

    @Test
    fun `read failed state exposes message for UI`() {
        val state = TypeCRfidPanelState(
            probeState = UsbProbeUiState.ReadFailed("timeout"),
        )

        val message = (state.probeState as UsbProbeUiState.ReadFailed).message
        assertEquals("timeout", message)
    }

    @Test
    fun `format permission state distinguishes pending denied and granted`() {
        assertEquals("待申请", formatPermissionState(UsbProbeUiState.DeviceDetected))
        assertEquals("未授予", formatPermissionState(UsbProbeUiState.PermissionDenied))
        assertEquals("已授予", formatPermissionState(UsbProbeUiState.Ready))
    }
}
```

- [ ] **Step 2: Run test to verify it fails if formatting helpers are missing**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.TypeCRfidTestPanelStateTest"`
Expected: FAIL until the panel formatting helpers exist.

- [ ] **Step 3: Implement the panel and lifecycle observation wiring**

`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt`

```kotlin
package com.ytone.longcare.features.nfctest.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytone.longcare.common.utils.UsbDeviceSummary
import com.ytone.longcare.common.utils.UsbEndpointSummary
import com.ytone.longcare.features.nfctest.vm.TypeCRfidPanelState
import com.ytone.longcare.features.nfctest.vm.UsbProbeUiState

@Composable
internal fun TypeCRfidTestPanel(
    state: TypeCRfidPanelState,
    activity: Activity?,
    onRefresh: () -> Unit,
    onRequestPermission: (Activity) -> Unit,
    onAttemptRead: (Activity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Type-C USB Host 测试",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            ProbeStatusChip(probeState = state.probeState)
            Text("设备: ${state.deviceSummary?.deviceName ?: "未检测到USB设备"}")
            Text("权限: ${formatPermissionState(state.probeState)}")
            Text("VID/PID: ${formatVidPid(state.deviceSummary)}")
            Text("Class/Subclass/Protocol: ${formatDeviceClassInfo(state.deviceSummary)}")
            Text("接口数: ${formatInterfaceCount(state.deviceSummary)}")
            Text("Endpoint摘要: ${formatEndpoints(state.deviceSummary?.endpoints)}")
            Text("原始文本: ${state.rawPayloadText ?: "-"}")
            Text("原始HEX: ${state.rawPayloadHex.ifBlank { "-" }}")
            Text("解析结果: ${state.parsedTagDisplay}")
            Text("最近更新时间: ${state.lastUpdatedAt ?: "-"}")

            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("刷新设备")
            }
            Button(
                onClick = { activity?.let(onRequestPermission) },
                modifier = Modifier.fillMaxWidth(),
                enabled = activity != null,
            ) {
                Text("申请权限")
            }
            Button(
                onClick = { activity?.let(onAttemptRead) },
                modifier = Modifier.fillMaxWidth(),
                enabled = activity != null,
            ) {
                Text("开始尝试读取")
            }
        }
    }
}

@Composable
private fun ProbeStatusChip(probeState: UsbProbeUiState) {
    val (label, icon, color) = when (probeState) {
        UsbProbeUiState.Idle -> Triple("待检测", Icons.Default.HourglassBottom, Color(0xFF8E8E93))
        UsbProbeUiState.NoDevice -> Triple("未检测到设备", Icons.Default.Error, Color(0xFF8E8E93))
        UsbProbeUiState.DeviceDetected -> Triple("已检测到设备", Icons.Default.CheckCircle, Color(0xFF3A86FF))
        UsbProbeUiState.PermissionDenied -> Triple("权限被拒绝", Icons.Default.Error, Color(0xFFD32F2F))
        UsbProbeUiState.Ready -> Triple("就绪", Icons.Default.CheckCircle, Color(0xFF2E7D32))
        UsbProbeUiState.Reading -> Triple("读取中", Icons.Default.HourglassBottom, Color(0xFFEF6C00))
        is UsbProbeUiState.ReadFailed -> Triple("读取失败", Icons.Default.Error, Color(0xFFD32F2F))
    }

    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (probeState is UsbProbeUiState.ReadFailed) "$label: ${probeState.message}" else label,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

internal fun formatPermissionState(probeState: UsbProbeUiState): String = when (probeState) {
    UsbProbeUiState.PermissionDenied -> "未授予"
    UsbProbeUiState.DeviceDetected -> "待申请"
    UsbProbeUiState.Ready, UsbProbeUiState.Reading, is UsbProbeUiState.ReadFailed -> "已授予"
    UsbProbeUiState.Idle, UsbProbeUiState.NoDevice -> "-"
}

internal fun formatVidPid(deviceSummary: UsbDeviceSummary?): String = if (deviceSummary == null) {
    "- / -"
} else {
    "0x%04X / 0x%04X".format(
        deviceSummary.vendorId and 0xFFFF,
        deviceSummary.productId and 0xFFFF,
    )
}

internal fun formatDeviceClassInfo(deviceSummary: UsbDeviceSummary?): String = if (deviceSummary == null) {
    "- / - / -"
} else {
    "${deviceSummary.deviceClass} / ${deviceSummary.deviceSubclass} / ${deviceSummary.deviceProtocol}"
}

internal fun formatInterfaceCount(deviceSummary: UsbDeviceSummary?): String =
    deviceSummary?.interfaceCount?.toString() ?: "-"

internal fun formatEndpoints(endpoints: List<UsbEndpointSummary>?): String = if (endpoints.isNullOrEmpty()) {
    "-"
} else {
    endpoints.joinToString(separator = "; ") { endpoint ->
        "addr=0x%02X dir=%d type=%d size=%d".format(
            endpoint.address and 0xFF,
            endpoint.direction,
            endpoint.type,
            endpoint.maxPacketSize,
        )
    }
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`

```kotlin
val typeCTestViewModel: TypeCRfidTestViewModel = hiltViewModel()
val typeCPanelState by typeCTestViewModel.panelState.collectAsStateWithLifecycle()
val activity = context as? Activity

LaunchedEffect(Unit) {
    typeCTestViewModel.refreshDevices()
}

LaunchedEffect(Unit) {
    typeCTestViewModel.startObserving()
}

DisposableEffect(Unit) {
    onDispose {
        typeCTestViewModel.stopObserving()
    }
}
```

`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`

```kotlin
@Composable
internal fun NfcTestBody(
    enabled: Boolean,
    typeCPanelState: TypeCRfidPanelState,
    activity: Activity?,
    onRefreshTypeC: () -> Unit,
    onRequestTypeCPermission: (Activity) -> Unit,
    onAttemptTypeCRead: (Activity) -> Unit,
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

        TypeCRfidTestPanel(
            state = typeCPanelState,
            activity = activity,
            onRefresh = onRefreshTypeC,
            onRequestPermission = onRequestTypeCPermission,
            onAttemptRead = onAttemptTypeCRead,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.TypeCRfidTestPanelStateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanel.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/TypeCRfidTestPanelStateTest.kt
git commit -m "feat: add Type-C USB host test panel"
```

### Task 5: Run regression and manual verification for the test panel

**Files:**
- Modify: `docs/superpowers/plans/2026-04-06-type-c-usb-host-test-panel.md` (check off during execution only)

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestContractsTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.TypeCRfidTestViewModelTest"
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.TypeCRfidTestPanelStateTest"
```

Expected: PASS for all three test classes.

- [ ] **Step 2: Run module-level safety checks**

Run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` and no new lint failures.

- [ ] **Step 3: Run manual no-device checklist**

Checklist:

```text
1. Open the NFC test page with no Type-C reader attached.
2. Verify the new panel is visible under the existing NFC test card.
3. Verify the status shows no device detected.
4. Tap refresh and confirm the state remains stable.
```

Expected: no crash, clear `未检测到USB设备`-style state.

- [ ] **Step 4: Run manual attached-device checklist**

Checklist:

```text
1. Attach the Type-C reader while the page remains open.
2. Verify the panel auto-refreshes and device summary fields populate.
3. Request permission and confirm the panel reflects the result.
4. Attempt a read while presenting a card.
5. Record whether raw text or hex payload appears.
6. Record whether parser output appears.
7. Remove the device while the page remains open and verify the panel auto-refreshes back to a no-device or disconnected state.
```

Expected: even if parsing fails, the panel should still expose useful probe information.

- [ ] **Step 5: Commit the finished feature branch state**

```bash
git status --short
# Verify only intended Type-C test panel files changed.
git add app/src/main/kotlin/com/ytone/longcare/features/nfctest \
  app/src/main/kotlin/com/ytone/longcare/common/utils \
  app/src/main/res/values/strings.xml \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest
git commit -m "feat: add Type-C USB host test diagnostics"
```

---

## Spec Coverage Check

- Reuse existing `NfcTestScreen`: Task 4 extends the current screen instead of adding a new route.
- Dedicated test-only state: Task 1 adds `UsbProbeUiState`, summaries, and panel state.
- Generic USB Host probe strategy: Task 2 adds a probe manager with refresh, permission check, attach/detach observation, interface inspection, and conservative read attempts.
- Raw payload and parser result display: Task 3 maps probe results into panel state; Task 4 renders them.
- Automatic attach/detach refresh: Task 2 adds observation, Task 3 reuses `refreshDevices()`, and Task 4 starts and stops observation with the screen lifecycle.
- Production safety: no task emits `AppEvent.TagScanned`; all tasks stay inside test-only screen/view model paths.
- Acceptance and verification: Task 5 covers automated tests plus the two manual checklists.

## Placeholder Scan

- No `TBD`, `TODO`, or deferred implementation notes remain.
- Each code-changing task includes exact file paths, code blocks, and runnable commands.
- The plan explicitly avoids introducing a new route or business-event emission.

## Type Consistency Check

- Diagnostic state is always `UsbProbeUiState`.
- Panel state is always `TypeCRfidPanelState`.
- Device metadata is always `UsbDeviceSummary` / `UsbEndpointSummary`.
- Low-level manager seam is always `UsbHostProbeManager`.
- Probe results are always `UsbHostProbeResult`.
- USB attach/detach events are always `UsbHostDeviceEvent`.
