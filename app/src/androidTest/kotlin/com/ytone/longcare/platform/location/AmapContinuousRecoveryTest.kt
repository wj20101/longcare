package com.ytone.longcare.platform.location

import android.net.ConnectivityManager
import android.os.Looper
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.features.location.core.AndroidLocationRuntimeReadiness
import com.ytone.longcare.features.location.manager.AmapClientHandle
import com.ytone.longcare.features.location.manager.AmapLocationClientFactory
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real SDK values and manager, isolated client: no Activity, location radio, account or care API. */
@RunWith(AndroidJUnit4::class)
class AmapContinuousRecoveryTest {
    @Test fun runtime_diagnostics_read_real_device_state_without_changing_it() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fields = AndroidLocationRuntimeReadiness(context).diagnosticSnapshot()
        assertEquals(Build.VERSION.SDK_INT, fields["androidApi"])
        assertTrue((fields["appVersionCode"] as? Long ?: 0L) > 0L)
        for (key in listOf("deviceIdle", "screenOn", "powerSave", "batteryExempt")) {
            assertTrue("Platform state unavailable: $key", fields[key] is Boolean)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue("Light idle state unavailable", fields["deviceLightIdle"] is Boolean)
            assertTrue("Low Power Standby setting unavailable", fields["lowPowerStandbyEnabled"] is Boolean)
        } else {
            assertTrue(fields.containsKey("deviceLightIdle"))
            assertNull(fields["deviceLightIdle"])
            assertTrue(fields.containsKey("lowPowerStandbyEnabled"))
            assertNull(fields["lowPowerStandbyEnabled"])
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertTrue("Background restriction unavailable", fields["backgroundRestricted"] is Boolean)
        } else {
            assertTrue(fields.containsKey("backgroundRestricted"))
            assertNull(fields["backgroundRestricted"])
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertTrue("Unexpected Data Saver policy", fields["restrictBackgroundStatus"] in setOf(
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED,
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            ))
        } else {
            assertTrue(fields.containsKey("restrictBackgroundStatus"))
            assertNull(fields["restrictBackgroundStatus"])
        }
        assertTrue(fields["networkAvailable"] is Boolean)
        if (fields["networkAvailable"] == false) {
            assertEquals(false, fields["networkValidated"])
            assertEquals("NONE", fields["networkTransport"])
        } else {
            assertTrue(fields["networkTransport"] == null ||
                fields["networkTransport"] in setOf("WIFI", "CELLULAR", "ETHERNET", "OTHER"))
        }
    }

    @Test fun repeated_network_errors_recover_on_the_same_client_without_success_emissions() = withFixture {
        start("order-a")
        val client = nextClient()
        repeat(8) { client.emit(networkFailure()) }
        val failure = manager.latestEvent() as LocationAcquisition.Failure
        assertEquals(LocationFailure.NETWORK, failure.reason)
        assertEquals(AMapLocation.ERROR_CODE_FAILURE_CONNECTION, failure.location?.errorCode)
        assertNoSuccess()
        assertClientActive(client)

        client.emit(validLocation())
        val recovered = successes.receive()
        assertEquals(failure.location?.requestId, recovered.requestId)
        assertEquals(AMapLocation.LOCATION_SUCCESS, recovered.errorCode)
        assertEquals(25f, recovered.accuracy)
        assertClientActive(client)
    }

    @Test fun stopped_client_callbacks_cannot_replace_a_new_owners_samples() = withFixture {
        val firstCollection = start("order-a")
        val firstClient = nextClient()
        firstClient.emit(validLocation())
        val firstSample = successes.receive()
        firstCollection.cancelAndJoin()
        val stoppedEvent = manager.latestEvent()
        firstClient.emit(networkFailure())
        firstClient.emit(validLocation())
        assertSame(stoppedEvent, manager.latestEvent())
        assertNoSuccess()
        withContext(Dispatchers.Main) {
            assertEquals(1, firstClient.stops)
            assertEquals(1, firstClient.unregisters)
            assertEquals(1, firstClient.destroys)
        }

        start("order-b")
        val nextClient = nextClient()
        assertNull(manager.latestEvent())
        firstClient.emit(networkFailure())
        firstClient.emit(validLocation())
        assertNull(manager.latestEvent())
        assertNoSuccess()
        nextClient.emit(validLocation())
        val nextSample = successes.receive()
        assertTrue(nextSample.requestId.isNotBlank())
        assertNotEquals(firstSample.requestId, nextSample.requestId)
        withContext(Dispatchers.Main) { assertEquals(2, clients.size) }
    }

    @Test fun null_and_released_callbacks_cannot_change_current_acquisition_state() = withFixture {
        val firstCollection = start("order-a")
        val previousClient = nextClient()
        previousClient.emit(null)
        assertNull(manager.latestEvent())
        previousClient.emit(networkFailure())
        val failure = manager.latestEvent()
        assertTrue(failure is LocationAcquisition.Failure)
        previousClient.emit(null)
        assertSame(failure, manager.latestEvent())
        previousClient.emit(validLocation())
        val previousSample = successes.receive()
        val success = manager.latestEvent()
        previousClient.emit(null)
        assertSame(success, manager.latestEvent())
        firstCollection.cancelAndJoin()

        start("order-b")
        val currentClient = nextClient()
        previousClient.emit(null)
        previousClient.emit(networkFailure())
        previousClient.emit(validLocation())
        assertNull(manager.latestEvent())
        currentClient.emitFromHandler(validLocation())
        assertNotEquals(previousSample.requestId, successes.receive().requestId)
    }

    @Test fun paused_sampling_keeps_the_collection_and_resumes_with_a_new_client_without_old_points() = withFixture {
        val collection = start("order-a")
        val oldClient = nextClient()
        oldClient.emit(validLocation())
        val oldSample = successes.receive()
        manager.setSamplingEnabled("order-a", false)
        oldClient.assertReleased()
        assertFalse(collection.isCompleted)
        assertEquals(LocationFailure.SERVICE_DISABLED, (manager.latestEvent() as LocationAcquisition.Failure).reason)
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), manager.acquireServiceOrderLocation())
        oldClient.emitFromHandler(validLocation())
        oldClient.emitFromHandler(networkFailure())
        assertEquals(LocationFailure.SERVICE_DISABLED, (manager.latestEvent() as LocationAcquisition.Failure).reason)
        assertNoSuccess()

        manager.setSamplingEnabled("order-a", true)
        val resumedClient = nextClient()
        assertNull(manager.latestEvent())
        oldClient.emitFromHandler(validLocation())
        assertNull(manager.latestEvent())
        assertNoSuccess()
        resumedClient.emitFromHandler(validLocation())
        val resumedSample = successes.receive()
        assertNotEquals(oldSample.requestId, resumedSample.requestId)
        assertFalse(collection.isCompleted)
        withContext(Dispatchers.Main) {
            assertEquals(2, clients.size)
            assertEquals(1, resumedClient.starts)
            assertEquals(0, resumedClient.destroys)
        }
    }

    @Test fun initially_disabled_sampling_waits_and_resumes_without_restarting_the_collection() = withFixture {
        val collection = start("order-a", initiallyEnabled = false)
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), acquisitions.receive())
        withContext(Dispatchers.Main) { assertTrue(clients.isEmpty()) }
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), manager.acquireServiceOrderLocation())
        assertFalse(collection.isCompleted)
        manager.setSamplingEnabled("order-a", true)
        val client = nextClient()
        client.emitFromHandler(validLocation())
        assertEquals(25f, successes.receive().accuracy)
        assertClientActive(client)
    }

    @Test fun pausing_sampling_completes_a_waiting_action_without_its_timeout() = withFixture {
        val collection = start("order-a")
        val client = nextClient()
        assertOption(client.options.receive(), intervalMs = 30_000L)
        val action = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        assertOption(client.options.receive(), intervalMs = 3_000L)
        manager.setSamplingEnabled("order-a", false)
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), action.await())
        assertFalse(collection.isCompleted)
        client.assertReleased()
    }

    @Test fun action_borrows_sampling_without_shortening_the_sdk_network_timeout() = withFixture {
        start("order-a")
        val client = nextClient()
        assertOption(client.options.receive(), intervalMs = 30_000L)

        // No usable warm sample: the action temporarily requests a faster cadence on this client.
        val action = scope.async { manager.acquireServiceOrderLocation() }
        assertOption(client.options.receive(), intervalMs = 3_000L)
        assertClientActive(client)
        action.cancelAndJoin()
        assertOption(client.options.receive(), intervalMs = 30_000L)
        assertClientActive(client)
    }

    @Test fun temporary_main_immediate_actions_complete_and_release_their_clients_from_handler_callbacks() = withFixture {
        val deniedAction = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        val deniedClient = nextClient()
        deniedClient.emitFromHandler(AMapLocation("network").apply {
            errorCode = AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION
        })
        assertEquals(LocationFailure.PERMISSION, (deniedAction.await() as LocationAcquisition.Failure).reason)
        deniedClient.assertReleased()

        val recoveredAction = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        val recoveredClient = nextClient()
        recoveredClient.emitFromHandler(networkFailure())
        assertFalse(recoveredAction.isCompleted)
        recoveredClient.emitFromHandler(validLocation())
        assertTrue(recoveredAction.await() is LocationAcquisition.Success)
        recoveredClient.assertReleased()
    }

    @Test fun temporary_actions_accept_fresh_gps_source_ticks_despite_wall_clock_offsets() = withFixture {
        for (wallOffsetMs in listOf(5_000L, -60_000L)) {
            val action = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
            val client = nextClient()
            val gps = validLocation().apply {
                locationType = AMapLocation.LOCATION_TYPE_GPS
                time = System.currentTimeMillis() + wallOffsetMs
                setLocationTickTime(SystemClock.elapsedRealtimeNanos())
            }
            client.emitFromHandler(gps)

            val result = action.await() as LocationAcquisition.Success
            assertEquals(AMapLocation.LOCATION_TYPE_GPS, result.location.locationType)
            assertEquals(gps.time, result.location.locationTime)
            assertEquals(gps.locationTickTime / 1_000_000L, result.location.sourceElapsedRealtime)
            client.assertReleased()
        }
    }

    @Test fun checkout_reuses_the_continuous_client_for_fresh_gps_with_an_offset_wall_clock() = withFixture {
        start("order-a")
        val client = nextClient()
        assertOption(client.options.receive(), intervalMs = 30_000L)
        val action = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        assertOption(client.options.receive(), intervalMs = 3_000L)
        val gps = validLocation().apply {
            locationType = AMapLocation.LOCATION_TYPE_GPS
            time = System.currentTimeMillis() - 60_000L
            setLocationTickTime(SystemClock.elapsedRealtimeNanos())
        }
        client.emitFromHandler(gps)

        val result = action.await() as LocationAcquisition.Success
        assertEquals(gps.time, result.location.locationTime)
        assertEquals(gps.locationTickTime / 1_000_000L, result.location.sourceElapsedRealtime)
        assertOption(client.options.receive(), intervalMs = 30_000L)
        assertClientActive(client)
    }

    @Test fun future_and_pre_action_source_ticks_cannot_complete_an_action_before_a_fresh_tick() = withFixture {
        val action = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        val client = nextClient()
        val future = validLocation().apply {
            locationType = AMapLocation.LOCATION_TYPE_GPS
            setLocationTickTime(SystemClock.elapsedRealtimeNanos() + 10_000_000_000L)
        }
        client.emitFromHandler(future)
        assertEquals(future.locationTickTime / 1_000_000L,
            (manager.latestEvent() as LocationAcquisition.Success).location.sourceElapsedRealtime)
        assertNull(withTimeoutOrNull(150L) { action.await() })
        assertFalse(action.isCompleted)
        val oldTick = SystemClock.elapsedRealtimeNanos() - 10_000_000_000L
        assertTrue("The test requires more than ten seconds of device uptime", oldTick > 0L)
        val reused = validLocation().apply {
            locationType = AMapLocation.LOCATION_TYPE_SAME_REQ
            // Reproduce the SDK refreshing wall times while retaining the previous source tick.
            setLocationUtcTime(time)
            setLocationTickTime(oldTick)
        }
        client.emitFromHandler(reused)
        val oldSample = (manager.latestEvent() as LocationAcquisition.Success).location
        assertEquals(reused.time, oldSample.locationTime)
        assertEquals(oldTick / 1_000_000L, oldSample.sourceElapsedRealtime)
        assertNull(withTimeoutOrNull(150L) { action.await() })
        assertFalse(action.isCompleted)
        assertClientActive(client)

        val freshTick = SystemClock.elapsedRealtimeNanos()
        val fresh = validLocation().apply {
            setLocationUtcTime(time)
            setLocationTickTime(freshTick)
        }
        client.emitFromHandler(fresh)
        val result = action.await() as LocationAcquisition.Success
        assertEquals(AMapLocation.LOCATION_TYPE_WIFI, result.location.locationType)
        assertEquals(freshTick / 1_000_000L, result.location.sourceElapsedRealtime)
        assertTrue(result.location.sourceElapsedRealtime > oldSample.sourceElapsedRealtime)
        client.assertReleased()
    }

    @Test fun unknown_source_ticks_keep_wall_clock_validation_until_a_valid_wall_time_arrives() = withFixture {
        val action = scope.async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        val client = nextClient()
        val future = validLocation().apply {
            time = System.currentTimeMillis() + 60_000L
            setLocationTickTime(0L)
        }
        client.emitFromHandler(future)
        val rejected = (manager.latestEvent() as LocationAcquisition.Success).location
        assertEquals(future.time, rejected.locationTime)
        assertEquals(0L, rejected.sourceElapsedRealtime)
        assertNull(withTimeoutOrNull(150L) { action.await() })
        assertFalse(action.isCompleted)
        assertClientActive(client)

        val valid = validLocation().apply { setLocationTickTime(0L) }
        client.emitFromHandler(valid)
        val result = action.await() as LocationAcquisition.Success
        assertEquals(valid.time, result.location.locationTime)
        assertEquals(0L, result.location.sourceElapsedRealtime)
        client.assertReleased()
    }

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clients = mutableListOf<FakeClient>()
        private val created = Channel<FakeClient>(Channel.UNLIMITED)
        val successes = Channel<LocationResult>(Channel.UNLIMITED)
        val acquisitions = Channel<LocationAcquisition>(Channel.UNLIMITED)
        val manager: ContinuousAmapLocationManager

        init {
            // MainApplication disables CrashReportGateway delivery for Debug, including fake failures.
            check(BuildConfig.DEBUG) { "Offline location tests require the Debug application" }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val factory = object : AmapLocationClientFactory(context, PrivacyConsentManager(context)) {
                override fun create(apiKey: String): AmapClientHandle {
                    assertMainThread()
                    return FakeClient().also {
                        clients += it
                        check(created.trySend(it).isSuccess)
                    }
                }
            }
            val keyProvider = object : AmapApiKeyProvider {
                override suspend fun getAmapApiKey() = "offline-test-key"
            }
            manager = ContinuousAmapLocationManager(factory, keyProvider, AndroidLocationRuntimeReadiness(context))
        }

        fun start(owner: String, initiallyEnabled: Boolean = true): Job = scope.launch {
            manager.startContinuousLocation(owner, initiallyEnabled).collect { event ->
                acquisitions.send(event)
                if (event is LocationAcquisition.Success) successes.send(event.location)
            }
        }

        suspend fun nextClient(): FakeClient = created.receive().also { it.started.await() }

        suspend fun assertNoSuccess() {
            assertNull(withTimeoutOrNull(150L) { successes.receive() })
        }

        suspend fun assertClientActive(client: FakeClient) = withContext(Dispatchers.Main) {
            assertEquals(1, clients.size)
            assertEquals(1, client.starts)
            assertEquals(0, client.stops)
            assertEquals(0, client.unregisters)
            assertEquals(0, client.destroys)
        }

        suspend fun close() = withContext(NonCancellable) {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    private class FakeClient : AmapClientHandle {
        val started = CompletableDeferred<Unit>()
        val options = Channel<AMapLocationClientOption>(Channel.UNLIMITED)
        private var callback: AMapLocationListener? = null
        var starts = 0
        var stops = 0
        var unregisters = 0
        var destroys = 0

        override fun setLocationOption(option: AMapLocationClientOption) {
            assertMainThread()
            check(options.trySend(option.clone()).isSuccess)
        }

        override fun setLocationListener(listener: AMapLocationListener) {
            assertMainThread()
            callback = listener
        }

        override fun unRegisterLocationListener(listener: AMapLocationListener) {
            assertMainThread()
            assertSame(callback, listener)
            unregisters += 1
            // Retain this reference solely to simulate an already queued SDK callback after cleanup.
        }

        override fun startLocation() {
            assertMainThread()
            starts += 1
            started.complete(Unit)
        }

        override fun stopLocation() {
            assertMainThread()
            stops += 1
        }

        override fun onDestroy() {
            assertMainThread()
            destroys += 1
        }

        suspend fun emit(location: AMapLocation?) = withContext(Dispatchers.Main) {
            requireNotNull(callback).onLocationChanged(location)
        }

        suspend fun emitFromHandler(location: AMapLocation) {
            val delivered = CompletableDeferred<Unit>()
            check(Handler(Looper.getMainLooper()).post {
                try {
                    requireNotNull(callback).onLocationChanged(location)
                    delivered.complete(Unit)
                } catch (error: Throwable) {
                    delivered.completeExceptionally(error)
                }
            })
            delivered.await()
        }

        suspend fun assertReleased() = withContext(Dispatchers.Main) {
            assertEquals(1, stops)
            assertEquals(1, unregisters)
            assertEquals(1, destroys)
        }
    }

    private companion object {
        fun withFixture(test: suspend Fixture.() -> Unit) = runBlocking {
            val fixture = Fixture()
            try {
                withTimeout(5_000L) { fixture.test() }
            } finally {
                fixture.close()
            }
        }

        fun assertMainThread() = assertEquals(Looper.getMainLooper(), Looper.myLooper())

        fun assertOption(option: AMapLocationClientOption, intervalMs: Long) {
            assertEquals(30_000L, option.httpTimeOut)
            assertEquals(intervalMs, option.interval)
            assertFalse(option.isOnceLocation)
            assertTrue(option.isOnceLocationLatest)
            assertEquals(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy, option.locationMode)
        }

        fun networkFailure() = AMapLocation("network").apply {
            errorCode = AMapLocation.ERROR_CODE_FAILURE_CONNECTION
            errorInfo = "offline test SocketTimeoutException"
        }

        fun validLocation() = AMapLocation("network").apply {
            latitude = 30.0
            longitude = 120.0
            accuracy = 25f
            time = System.currentTimeMillis()
            coordType = AMapLocation.COORD_TYPE_GCJ02
            locationType = AMapLocation.LOCATION_TYPE_WIFI
            trustedLevel = AMapLocation.TRUSTED_LEVEL_HIGH
        }
    }
}
