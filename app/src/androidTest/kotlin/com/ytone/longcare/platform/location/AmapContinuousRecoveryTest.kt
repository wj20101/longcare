package com.ytone.longcare.platform.location

import android.os.Looper
import android.os.Build
import android.os.Handler
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

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clients = mutableListOf<FakeClient>()
        private val created = Channel<FakeClient>(Channel.UNLIMITED)
        val successes = Channel<LocationResult>(Channel.UNLIMITED)
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

        fun start(owner: String): Job = scope.launch {
            manager.startContinuousLocation(owner).collect { successes.send(it) }
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

        suspend fun emit(location: AMapLocation) = withContext(Dispatchers.Main) {
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
