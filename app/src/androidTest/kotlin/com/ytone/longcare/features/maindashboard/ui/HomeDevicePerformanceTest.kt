package com.ytone.longcare.features.maindashboard.ui

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.MainActivity
import com.ytone.longcare.domain.home.HomeDeviceRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.vm.HomeDeviceViewModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.NavigationStateTestActivity
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in physical-device comparison, independent of Compose's instrumentation test clock. */
class HomeDevicePerformanceTest {
    private val status = ZhiChiDeviceStatusModel(
        deviceSn = "TEST-DEVICE", status = "在线 · 网络正常", statusColor = "#27AE60",
        installUrl = "https://device.invalid/install", previewUrl = "https://device.invalid/preview",
    )

    /**
     * Opt in with -e runHomeDevicePerformance true on an unlocked, otherwise idle test device.
     * External CPU/PSS/frame collectors can synchronize on HomeDeviceProbe phase messages.
     * All sessions and responses below are in memory; no business API or stored account is used.
     */
    @Test
    fun realHomeLifecycleWithPerformanceSampling() {
        assumeTrue("Long-running real Home test requires runHomeDevicePerformance=true",
            InstrumentationRegistry.getArguments().getString("runHomeDevicePerformance") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // No Compose test rule or test clock: the host uses the device's natural frame clock.
        val scenario = ActivityScenario.launch(NavigationStateTestActivity::class.java)
        lateinit var host: NavigationStateTestActivity
        scenario.onActivity { host = it }
        val power = host.getSystemService(PowerManager::class.java)
        val keyguard = host.getSystemService(KeyguardManager::class.java)
        val repository = DeviceProbeRepository(status)
        val session = object : UserSessionRepository {
            override val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedIn(User(userIdentity = 1)))
            override suspend fun login(user: User) { sessionState.value = SessionState.LoggedIn(user) }
            override suspend fun updateUser(user: User) = login(user)
            override suspend fun logout() { sessionState.value = SessionState.LoggedOut }
        }
        val viewModel = HomeDeviceViewModel(repository, session)
        instrumentation.runOnMainSync { host.viewModelStore.put("homeDeviceProbe", viewModel) }
        val selected = mutableStateOf(false)
        val baselineCard = mutableStateOf(true)
        val phases = JSONArray()
        val processSamples = JSONArray()
        val lifecycleEvents = CopyOnWriteArrayList<JSONObject>()
        val checks = JSONObject()
        val lastResumedAt = AtomicLong()
        var failure: Throwable? = null
        var originalKeepScreenOn = false
        var changedWindowFlag = false

        fun lifecycleState(): Lifecycle.State {
            var state = Lifecycle.State.DESTROYED
            instrumentation.runOnMainSync { state = host.lifecycle.currentState }
            return state
        }
        fun waitFor(message: String, timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25L)
            assertTrue(message, condition())
        }
        fun verify(name: String, value: Boolean) {
            checks.put(name, value)
            assertTrue(name, value)
        }
        fun phase(name: String) {
            val state = lifecycleState().name
            val now = SystemClock.elapsedRealtime()
            phases.put(JSONObject().put("phase", name).put("elapsedRealtime", now)
                .put("isInteractive", power.isInteractive).put("lifecycle", state)
                .put("calls", repository.calls.size).put("inFlight", repository.inFlight.get()))
            Log.i("HomeDeviceProbe", phases.getJSONObject(phases.length() - 1).toString())
            instrumentation.sendStatus(0, Bundle().apply {
                putString("homeDevicePhase", name)
                putLong("homeDeviceElapsedRealtime", now)
                putBoolean("homeDeviceInteractive", power.isInteractive)
                putString("homeDeviceLifecycle", state)
                putInt("homeDeviceCalls", repository.calls.size)
            })
        }
        fun sampleWindow(name: String, startAction: () -> Unit = {}): JSONObject {
            phase(name)
            val startedAt = SystemClock.elapsedRealtime()
            val cpuAtStart = Process.getElapsedCpuTime()
            val samples = JSONArray()
            fun sample() {
                samples.put(JSONObject().put("elapsedRealtime", SystemClock.elapsedRealtime())
                    .put("processCpuMillis", Process.getElapsedCpuTime()).put("pssKb", Debug.getPss()))
            }
            sample()
            startAction()
            repeat(4) { index ->
                SystemClock.sleep((startedAt + (index + 1) * 15_000L - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                sample()
            }
            return JSONObject().put("phase", name).put("startedAt", startedAt)
                .put("finishedAt", SystemClock.elapsedRealtime())
                .put("processCpuDeltaMillis", Process.getElapsedCpuTime() - cpuAtStart)
                .put("samples", samples).also { processSamples.put(it) }
        }
        fun restoreHost() {
            instrumentation.runOnMainSync {
                host.startActivity(Intent(host, NavigationStateTestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            waitFor("Home return did not resume the same unlocked test host") {
                power.isInteractive && !keyguard.isKeyguardLocked && lifecycleState() == Lifecycle.State.RESUMED
            }
        }
        fun awaitStopped(name: String) {
            waitFor("$name did not cancel the visible-home poll") {
                !lifecycleState().isAtLeast(Lifecycle.State.RESUMED) &&
                    viewModel.deviceStatus.value == status && repository.inFlight.get() == 0
            }
        }
        fun verifyImmediateReturn(name: String, before: Int) {
            verify("${name}_retains_last_successful_status", viewModel.deviceStatus.value == status)
            waitFor("$name did not refresh after resume", 2_000L) {
                repository.calls.size == before + 1 && viewModel.deviceStatus.value == status
            }
            val latency = repository.calls.last().startedAt - lastResumedAt.get()
            verify("${name}_refresh_within_2s", latency in -100L..2_000L)
        }
        val observer = LifecycleEventObserver { _, event ->
            val now = SystemClock.elapsedRealtime()
            if (event == Lifecycle.Event.ON_RESUME) lastResumedAt.set(now)
            lifecycleEvents += JSONObject().put("event", event.name).put("elapsedRealtime", now)
                .put("state", host.lifecycle.currentState.name).put("isInteractive", power.isInteractive)
        }

        try {
            verify("starts_unlocked_and_interactive", power.isInteractive && !keyguard.isKeyguardLocked)
            instrumentation.runOnMainSync {
                originalKeepScreenOn = host.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                changedWindowFlag = true
                host.lifecycle.addObserver(observer)
            }
            instrumentation.runOnMainSync {
                host.setContent {
                    LongCareTheme {
                        HomeDevicePollingEffect(viewModel, selected.value, host)
                        val currentStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
                        Column(Modifier.width(328.dp).background(bgGradientBrush).padding(12.dp)) {
                            (if (baselineCard.value) status else currentStatus)?.let { HomeDeviceStatusCard(it) { _, _ -> } }
                        }
                    }
                }
            }
            phase("warmup_10s")
            SystemClock.sleep(10_000L)
            sampleWindow("baseline_60s")
            verify("baseline_has_no_requests", repository.calls.isEmpty())
            verify("baseline_remained_resumed", lifecycleState() == Lifecycle.State.RESUMED && power.isInteractive)

            val foregroundSample = sampleWindow("foreground_60s") {
                instrumentation.runOnMainSync { baselineCard.value = false; selected.value = true }
                waitFor("foreground polling did not produce the first status", 2_000L) { viewModel.deviceStatus.value != null }
            }
            val foregroundStart = foregroundSample.getLong("startedAt")
            val foregroundEnd = foregroundSample.getLong("finishedAt")
            val foregroundCalls = repository.calls.filter { it.startedAt in foregroundStart..foregroundEnd }
            verify("foreground_11_to_13_calls_in_60s", foregroundCalls.size in 11..13)
            verify("foreground_intervals_at_least_4800ms",
                foregroundCalls.zipWithNext().all { (first, next) -> next.startedAt - first.startedAt >= 4_800L })
            verify("foreground_remained_resumed", lifecycleState() == Lifecycle.State.RESUMED && power.isInteractive)

            phase("home_requested")
            verify("home_action_accepted", instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            awaitStopped("Home")
            val homeCount = repository.calls.size
            phase("home_background_15s")
            SystemClock.sleep(15_000L)
            verify("home_no_requests_for_15s", repository.calls.size == homeCount)
            verify("home_retains_last_successful_status", viewModel.deviceStatus.value == status)
            phase("home_return")
            restoreHost()
            verifyImmediateReturn("home_return", homeCount)

            phase("repeat_home_return_5_times")
            repeat(5) { index ->
                verify("repeat_${index}_home_accepted",
                    instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
                awaitStopped("repeated Home $index")
                val before = repository.calls.size
                restoreHost()
                verifyImmediateReturn("repeat_$index", before)
            }
            val beforeFinalInterval = repository.calls.size
            phase("final_interval_5200ms")
            SystemClock.sleep(5_200L)
            verify("repeated_returns_have_one_polling_loop", repository.calls.size == beforeFinalInterval + 1)
            verify("maximum_one_request_in_flight", repository.maxInFlight.get() == 1)
            phase("completed")
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            var cleanupError: Throwable? = null
            try {
                val retainedStatus = viewModel.deviceStatus.value
                instrumentation.runOnMainSync {
                    selected.value = false
                    // Dispose the polling effect before restoring normal product content.
                    host.findViewById<ViewGroup>(android.R.id.content).removeAllViews()
                    host.lifecycle.removeObserver(observer)
                    if (changedWindowFlag && !originalKeepScreenOn) {
                        host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                waitFor("cleanup did not cancel polling") { repository.inFlight.get() == 0 }
                verify("cleanup_keeps_last_successful_status", viewModel.deviceStatus.value == retainedStatus)
            } catch (error: Throwable) {
                cleanupError = error
            } finally {
                try {
                    scenario.close()
                    context.startActivity(Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                } catch (error: Throwable) {
                    if (cleanupError == null) cleanupError = error
                }
            }
            val report = JSONObject().put("success", failure == null && cleanupError == null)
                .put("samplingScope", "isolated production lifecycle and status card; fake 50ms repository; no network")
                .put("buildScope", "debug APK plus instrumentation; comparison only, not a release benchmark")
                .put("frameClock", "natural device vsync; no ComposeTestRule")
                .put("phases", phases).put("lifecycleEvents", JSONArray(lifecycleEvents))
                .put("processSamples", processSamples)
                .put("calls", JSONArray(repository.calls.map { it.toJson() }))
                .put("maxInFlight", repository.maxInFlight.get())
                .put("cancelledCalls", repository.calls.count { it.cancelled }).put("checks", checks)
                .put("failure", failure?.javaClass?.simpleName ?: JSONObject.NULL)
                .put("cleanupFailure", cleanupError?.javaClass?.simpleName ?: JSONObject.NULL)
            val directory = requireNotNull(context.getExternalFilesDir("home-device-tests"))
            File(directory, "home-device-performance.json").writeText(report.toString(2))
            if (failure == null && cleanupError != null) throw cleanupError
        }
    }

    private class DeviceProbeRepository(private val response: ZhiChiDeviceStatusModel) : HomeDeviceRepository {
        val calls = CopyOnWriteArrayList<ProbeCall>()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()

        override suspend fun getDeviceStatus(): ApiResult<ZhiChiDeviceStatusModel> {
            val call = ProbeCall(SystemClock.elapsedRealtime())
            calls += call
            val active = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, active) }
            try {
                delay(50L)
                return ApiResult.Success(response)
            } catch (error: CancellationException) {
                call.cancelled = true
                throw error
            } finally {
                call.finishedAt = SystemClock.elapsedRealtime()
                inFlight.decrementAndGet()
            }
        }
    }

    private class ProbeCall(val startedAt: Long) {
        @Volatile var finishedAt = 0L
        @Volatile var cancelled = false
        fun toJson(): JSONObject = JSONObject().put("startedAt", startedAt).put("finishedAt", finishedAt)
            .put("cancelled", cancelled)
    }
}
