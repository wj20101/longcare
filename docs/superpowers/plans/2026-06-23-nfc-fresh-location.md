# NFC Fresh Location Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make NFC sign-in/sign-out submit a fresh AMap location instead of app cache or shared Flow replay.

**Architecture:** Add an explicit fresh-location API to `LocationFacade`, implement it with an isolated AMap one-shot request, and route only NFC through that API. Keep cached `getCurrentLocation()` and continuous upload unchanged for non-NFC callers.

**Tech Stack:** Kotlin, Android, AMap Location SDK, Hilt, Coroutines, MockK, JUnit4, Gradle.

## Global Constraints

- NFC distance validation must use fresh AMap coordinates.
- Server and client coordinates are both AMap coordinates; do not add coordinate conversion.
- Do not change continuous location upload or the local `order_locations` retry queue.
- Do not add client-side distance calculation.
- Do not fall back to stale app cache when NFC fresh location fails.
- Do not add an accuracy threshold in this pass.
- NFC fresh-location timeout is 10 seconds by default.

---

## File Structure

- `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`
  - Defines `getFreshLocation()` and the 10 second default timeout.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
  - Routes fresh location directly to AMap without reading `LocationStateManager` cache first.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`
  - Creates an isolated one-shot AMap client for fresh NFC location.
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
  - Uses the fresh-location API for NFC coordinates.
- `app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCancellationTest.kt`
  - Covers cache bypass and cancellation behavior.
- `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt`
  - Guards the implementation against shared Flow replay and SDK cache regressions.
- `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegateTest.kt`
  - Proves NFC calls fresh location and does not call cached current location.

---

### Task 1: Add Fresh Location API and Isolated AMap Implementation

**Files:**
- Modify: `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCancellationTest.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt`

**Interfaces:**
- Consumes: existing `LocationStateManager`, `LocationEventTracker`, and AMap SDK classes.
- Produces:
  - `LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS: Long`
  - `LocationFacade.getFreshLocation(timeoutMs: Long = DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?`
  - `DefaultLocationFacade.getFreshLocation(timeoutMs: Long): LocationResult?`
  - `ContinuousAmapLocationManager.getFreshLocation(timeoutMs: Long = LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?`

- [ ] **Step 1: Write failing facade tests**

Add these tests to `app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCancellationTest.kt`:

```kotlin
@Test
fun `getFreshLocation should bypass business cache and request fresh amap location`() = runTest {
    val amap = mockk<ContinuousAmapLocationManager>()
    val stateManager = mockk<LocationStateManager>(relaxed = true)
    val keepAliveManager = mockk<LocationKeepAliveManager>()
    val cachedLocation = LocationResult(
        latitude = 30.0,
        longitude = 120.0,
        provider = "cached",
        accuracy = 10f
    )
    val freshLocation = LocationResult(
        latitude = 31.2304,
        longitude = 121.4737,
        provider = "amap_fresh",
        accuracy = 8f
    )

    every { stateManager.getValidLocation(any()) } returns cachedLocation
    coEvery { amap.getFreshLocation(any()) } returns freshLocation

    val facade = DefaultLocationFacade(
        continuousAmapLocationManager = amap,
        locationStateManager = stateManager,
        locationKeepAliveManager = keepAliveManager
    )

    val result = facade.getFreshLocation(timeoutMs = 4_000L)

    assertSame(freshLocation, result)
    verify(exactly = 0) { stateManager.getValidLocation(any()) }
    coVerify(exactly = 1) { amap.getFreshLocation(8_000L) }
}

@Test
fun `getFreshLocation should rethrow cancellation from amap source`() = runTest {
    val amap = mockk<ContinuousAmapLocationManager>()
    val stateManager = mockk<LocationStateManager>(relaxed = true)
    val keepAliveManager = mockk<LocationKeepAliveManager>()

    coEvery { amap.getFreshLocation(any()) } throws CancellationException("fresh cancelled")

    val facade = DefaultLocationFacade(
        continuousAmapLocationManager = amap,
        locationStateManager = stateManager,
        locationKeepAliveManager = keepAliveManager
    )

    val cancellation = try {
        facade.getFreshLocation(timeoutMs = 10_000L)
        null
    } catch (e: CancellationException) {
        e
    }

    assertNotNull(cancellation)
    verify(exactly = 0) { stateManager.getValidLocation(any()) }
}
```

- [ ] **Step 2: Write failing source regression test**

Create `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt`:

```kotlin
package com.ytone.longcare.features.location.manager

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAmapLocationManagerFreshLocationSourceTest {

    @Test
    fun `fresh location uses isolated amap client and disables cached replay sources`() {
        val source = File(
            "../feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt"
        ).readText()
        val method = source
            .substringAfter("suspend fun getFreshLocation")
            .substringBefore("/**\n     * 权限授予后重启定位引擎")

        assertTrue(method.contains("AMapLocationClient(context)"))
        assertTrue(method.contains("suspendCancellableCoroutine"))
        assertTrue(method.contains("AMapLocationClientOption.AMapLocationPurpose.SignIn"))
        assertTrue(method.contains("setOnceLocation(true)"))
        assertTrue(method.contains("setOnceLocationLatest(true)"))
        assertTrue(method.contains("setLocationCacheEnable(false)"))
        assertFalse(method.contains("startContinuousLocation().first()"))
        assertFalse(method.contains("getCurrentLocation(timeoutMs)"))
    }
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.core.DefaultLocationFacadeCancellationTest' \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest'
```

Expected: FAIL at compile time because `getFreshLocation` does not exist.

- [ ] **Step 4: Add the domain contract**

Edit `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt` so it contains:

```kotlin
interface LocationFacade {
    fun observeLocations(intervalMs: Long = 30_000L): Flow<LocationResult>

    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_FAST_LOCATION_TIMEOUT_MS): LocationResult?

    suspend fun getFreshLocation(timeoutMs: Long = DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?

    fun getCachedLocation(maxAgeMs: Long = 30_000L): LocationResult?

    fun acquireKeepAlive(owner: String)

    fun releaseKeepAlive(owner: String)

    /** 定位权限授予后调用，重启定位引擎 */
    fun notifyPermissionGranted() {}

    companion object {
        const val DEFAULT_FAST_LOCATION_TIMEOUT_MS: Long = 4_000L
        const val DEFAULT_FRESH_LOCATION_TIMEOUT_MS: Long = 10_000L
        const val BUSINESS_LOCATION_CACHE_MAX_AGE_MS: Long = 5 * 60 * 1000L
    }
}
```

- [ ] **Step 5: Add facade fresh-location routing**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`.

Add this method inside `DefaultLocationFacade`:

```kotlin
override suspend fun getFreshLocation(timeoutMs: Long): LocationResult? {
    val boundedTimeoutMs = timeoutMs.coerceIn(
        MIN_FRESH_LOCATION_TIMEOUT_MS,
        MAX_FRESH_LOCATION_TIMEOUT_MS
    )

    val amapResult = try {
        continuousAmapLocationManager.getFreshLocation(boundedTimeoutMs)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LocationEventTracker.trackError(
            LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_ERROR,
            throwable = e,
            extras = mapOf("errorMsg" to e.message)
        )
        null
    }
    if (amapResult != null) {
        locationStateManager.recordLocationSuccess(amapResult)
    }
    return amapResult
}
```

Replace the private companion object with:

```kotlin
private companion object {
    const val MIN_LOCATION_TIMEOUT_MS = 1_000L
    const val MAX_LOCATION_TIMEOUT_MS = LocationFacade.DEFAULT_FAST_LOCATION_TIMEOUT_MS
    const val MIN_FRESH_LOCATION_TIMEOUT_MS = 8_000L
    const val MAX_FRESH_LOCATION_TIMEOUT_MS = 15_000L
}
```

Keep the existing `getCurrentLocation()` body unchanged.

- [ ] **Step 6: Add AMap fresh-location imports**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt` imports:

```kotlin
import com.ytone.longcare.domain.location.LocationFacade
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
```

- [ ] **Step 7: Add fresh timeout constants**

Add these constants to the `companion object` in `ContinuousAmapLocationManager.kt`:

```kotlin
/** NFC新鲜定位最小超时，低于高德建议值时自动提升 */
const val MIN_FRESH_TIMEOUT = 8_000L
/** NFC新鲜定位最大超时，避免用户在扫码后等待过久 */
const val MAX_FRESH_TIMEOUT = 15_000L
```

- [ ] **Step 8: Add fresh-location option builder**

Add this private method near `buildContinuousLocationOption()`:

```kotlin
private fun buildFreshLocationOption(timeoutMs: Long): AMapLocationClientOption {
    return AMapLocationClientOption().apply {
        setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.SignIn)
        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        isNeedAddress = false
        setOnceLocation(true)
        setOnceLocationLatest(true)
        isWifiScan = true
        isMockEnable = false
        setLocationCacheEnable(false)
        httpTimeOut = timeoutMs.coerceIn(MIN_FRESH_TIMEOUT, MAX_FRESH_TIMEOUT)
    }
}
```

- [ ] **Step 9: Add isolated fresh-location method**

Add this method to `ContinuousAmapLocationManager.kt` after the existing `getCurrentLocation()` method and before `restartAfterPermissionGrant()`:

```kotlin
suspend fun getFreshLocation(timeoutMs: Long = LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult? {
    val apiKey = amapApiKeyProvider.getAmapApiKey()?.takeIf { it.isNotBlank() } ?: ""

    if (apiKey.isBlank()) {
        LocationEventTracker.trackError(LocationEventTracker.EventType.API_KEY_UNAVAILABLE)
        return null
    }

    val boundedTimeoutMs = timeoutMs.coerceIn(MIN_FRESH_TIMEOUT, MAX_FRESH_TIMEOUT)
    return try {
        withTimeoutOrNull(boundedTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                AMapLocationClient.setApiKey(apiKey)
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)

                val finished = AtomicBoolean(false)
                var client: AMapLocationClient? = null
                var listener: AMapLocationListener? = null

                fun cleanup() {
                    try {
                        listener?.let { client?.unRegisterLocationListener(it) }
                        client?.stopLocation()
                        client?.onDestroy()
                    } catch (e: Exception) {
                        LocationEventTracker.trackError(
                            LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                            throwable = e,
                            extras = mapOf("errorMsg" to e.message)
                        )
                    }
                }

                fun finish(result: LocationResult?) {
                    if (!finished.compareAndSet(false, true)) return
                    cleanup()
                    continuation.resume(result)
                }

                try {
                    client = AMapLocationClient(context)
                    listener = AMapLocationListener { location: AMapLocation? ->
                        if (location != null && location.errorCode == 0) {
                            finish(
                                LocationResult(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    provider = "amap_fresh",
                                    accuracy = location.accuracy
                                )
                            )
                        } else {
                            LocationEventTracker.trackError(
                                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                                extras = mapOf(
                                    "errorCode" to location?.errorCode,
                                    "errorMsg" to (location?.errorInfo ?: "未知错误")
                                )
                            )
                            finish(null)
                        }
                    }
                    client?.setLocationListener(listener)
                    client?.setLocationOption(buildFreshLocationOption(boundedTimeoutMs))
                    client?.startLocation()
                } catch (e: Exception) {
                    LocationEventTracker.trackError(
                        LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
                        throwable = e,
                        extras = mapOf("errorMsg" to e.message)
                    )
                    finish(null)
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        cleanup()
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LocationEventTracker.trackError(
            LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL,
            throwable = e,
            extras = mapOf("errorMsg" to e.message)
        )
        null
    }
}
```

- [ ] **Step 10: Run focused location tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.core.DefaultLocationFacadeCancellationTest' \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest'
```

Expected: PASS.

- [ ] **Step 11: Commit Task 1**

```bash
git add core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt \
  app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCancellationTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt
git commit -m "feat: add isolated fresh location"
```

---

### Task 2: Route NFC Through Fresh Location

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegateTest.kt`

**Interfaces:**
- Consumes: `LocationFacade.getFreshLocation(timeoutMs: Long = LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?`.
- Produces: NFC coordinate lookup uses fresh location and returns `Pair(longitude, latitude)`.

- [ ] **Step 1: Write failing NFC delegate tests**

Add imports to `NfcActivityAndLocationDelegateTest.kt`:

```kotlin
import com.ytone.longcare.model.LocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
```

Add these tests:

```kotlin
@Test
fun `getCurrentLocationCoordinates uses fresh location for NFC`() = runTest {
    val locationFacade = mockk<LocationFacade>(relaxed = true)
    val delegate = NfcActivityAndLocationDelegate(
        context = mockk<Context>(relaxed = true),
        nfcManager = mockk<NfcManager>(relaxed = true),
        locationFacade = locationFacade,
    )
    coEvery { locationFacade.getFreshLocation(any()) } returns LocationResult(
        latitude = 31.2304,
        longitude = 121.4737,
        provider = "amap_fresh",
        accuracy = 8f
    )

    val coordinates = delegate.getCurrentLocationCoordinates()

    assertEquals(Pair("121.4737", "31.2304"), coordinates)
    coVerify(exactly = 1) {
        locationFacade.getFreshLocation(LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS)
    }
    coVerify(exactly = 0) { locationFacade.getCurrentLocation(any()) }
}

@Test
fun `getCurrentLocationCoordinates returns blank coordinates when fresh location is unavailable`() = runTest {
    val locationFacade = mockk<LocationFacade>(relaxed = true)
    val delegate = NfcActivityAndLocationDelegate(
        context = mockk<Context>(relaxed = true),
        nfcManager = mockk<NfcManager>(relaxed = true),
        locationFacade = locationFacade,
    )
    coEvery { locationFacade.getFreshLocation(any()) } returns null

    val coordinates = delegate.getCurrentLocationCoordinates()

    assertEquals(Pair("", ""), coordinates)
    coVerify(exactly = 1) {
        locationFacade.getFreshLocation(LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS)
    }
}
```

- [ ] **Step 2: Run NFC delegate tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.vm.NfcActivityAndLocationDelegateTest'
```

Expected: FAIL because `NfcActivityAndLocationDelegate` still calls `getCurrentLocation()`.

- [ ] **Step 3: Change NFC delegate to fresh location**

Edit `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`:

```kotlin
suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
    return try {
        val location = locationFacade.getFreshLocation()
        if (location != null) {
            Pair(location.longitude.toString(), location.latitude.toString())
        } else {
            Pair("", "")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Pair("", "")
    }
}
```

- [ ] **Step 4: Run NFC delegate tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.vm.NfcActivityAndLocationDelegateTest'
```

Expected: PASS.

- [ ] **Step 5: Run NFC blank-coordinate regression test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.ytone.longcare.features.nfc.ui.NfcWorkflowLocationResultTest'
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegateTest.kt
git commit -m "fix: use fresh location for nfc"
```

---

### Task 3: Final Regression and Build Verification

**Files:**
- Verify: `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`
- Verify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
- Verify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`
- Verify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`

**Interfaces:**
- Consumes:
  - `LocationFacade.getCurrentLocation(timeoutMs: Long): LocationResult?`
  - `LocationFacade.getFreshLocation(timeoutMs: Long): LocationResult?`
  - `LocationFacade.observeLocations(intervalMs: Long): Flow<LocationResult>`
- Produces: a verified implementation ready for code review.

- [ ] **Step 1: Run all focused location and NFC tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.core.DefaultLocationFacadeCancellationTest' \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest' \
  --tests 'com.ytone.longcare.features.nfc.vm.NfcActivityAndLocationDelegateTest' \
  --tests 'com.ytone.longcare.features.nfc.ui.NfcWorkflowLocationResultTest' \
  --tests 'com.ytone.longcare.features.location.reporting.LocationReportingManagerTest'
```

Expected: PASS.

- [ ] **Step 2: Compile debug Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Review the implementation diff**

Run:

```bash
git diff --stat HEAD~2..HEAD
git diff HEAD~2..HEAD -- core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt
```

Expected:
- `getCurrentLocation()` still checks `LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS`.
- `getFreshLocation()` does not call `LocationStateManager.getValidLocation()`.
- `ContinuousAmapLocationManager.getFreshLocation()` creates `AMapLocationClient(context)`.
- `ContinuousAmapLocationManager.getFreshLocation()` does not call `startContinuousLocation().first()`.
- NFC delegate calls `locationFacade.getFreshLocation()`.

---

## Self-Review

- Spec coverage: Task 1 adds the fresh API, cache bypass, and isolated one-shot AMap request. Task 2 routes NFC through fresh location. Task 3 verifies the focused business flow and confirms continuous reporting remains separate.
- Placeholder scan: no deferred placeholders remain.
- Type consistency: the plan uses `getFreshLocation(timeoutMs: Long): LocationResult?`, `LocationResult(latitude, longitude, provider, accuracy)`, and `Pair(longitude, latitude)` consistently.
