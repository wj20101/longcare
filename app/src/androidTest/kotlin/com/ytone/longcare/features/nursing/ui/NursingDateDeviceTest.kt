package com.ytone.longcare.features.nursing.ui

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.vm.NursingViewModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.NavigationStateTestActivity
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Activity/Compose lifecycle with isolated orders and dates; no account or system clock edits. */
class NursingDateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private val orders = RecordingOrders()
    private val now = AtomicReference(LocalDate(2026, 11, 3))
    private val dateReads = AtomicInteger()
    private val tabSelected = mutableStateOf(true)

    @After fun releaseFixture() {
        instrumentation.runOnMainSync {
            NavigationStateTestActivity.recreatedContent = null
            orders.cancelHeldResponse()
            store.clear()
        }
    }

    @Test fun visiblePageChecksMidnightWithoutRepeatingSameDayRequests() {
        show()
        assertSelected("11/03")
        assertOrder("2026-11-03")
        advanceDateCheck()
        assertRequests("2026-11-03")

        now.set(LocalDate(2026, 11, 4))
        advanceDateCheck()

        assertSelected("11/04")
        assertOrder("2026-11-04")
        assertRequests("2026-11-03", "2026-11-04")
        recordSuccess("visible-midnight")
    }

    @Test fun sameDayActivityRecreationKeepsSelectionAndRequestsOnlyThatDay() {
        show()
        select("11/02")
        recreateOn(now.get())

        assertSelected("11/02")
        assertOrder("2026-11-02")
        assertRequests("2026-11-02")
        recordSuccess("same-day-recreation")
    }

    @Test fun crossDayActivityRecreationKeepsManualSelectionInTheSameMonth() {
        show()
        select("11/02")
        recreateOn(LocalDate(2026, 11, 4))

        assertSelected("11/02")
        assertOrder("2026-11-02")
        assertRequests("2026-11-02")
        recordSuccess("cross-day-manual-selection")
    }

    @Test fun crossDayActivityRecreationFollowsTheNewToday() {
        show()
        assertSelected("11/03")
        recreateOn(LocalDate(2026, 11, 4))

        assertSelected("11/04")
        assertOrder("2026-11-04")
        assertRequests("2026-11-04")
        recordSuccess("cross-day-today")
    }

    @Test fun shorterMonthActivityRecreationDropsAnOutOfMonthSelection() {
        now.set(LocalDate(2026, 1, 31))
        show()
        select("01/30")
        recreateOn(LocalDate(2026, 2, 1))

        assertSelected("02/01")
        assertOrder("2026-02-01")
        assertRequests("2026-02-01")
        recordSuccess("shorter-month-recreation")
    }

    @Test fun backgroundStopsDateChecksAndResumeRefreshesImmediately() {
        show()
        assertOrder("2026-11-03")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val readsBefore = dateReads.get()
        now.set(LocalDate(2026, 11, 4))
        advanceDateCheck()
        assertEquals("background page must not check dates", readsBefore, dateReads.get())
        assertRequests("2026-11-03")

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        assertSelected("11/04")
        assertOrder("2026-11-04")
        assertRequests("2026-11-03", "2026-11-04")
        recordSuccess("background-resume")
    }

    @Test fun hiddenTabStopsDateChecksAndReturnKeepsTheManualDate() {
        show()
        select("11/02")
        compose.runOnIdle { tabSelected.value = false }
        val readsBefore = dateReads.get()
        now.set(LocalDate(2026, 11, 4))
        advanceDateCheck()
        assertEquals("hidden tab must not check dates", readsBefore, dateReads.get())
        assertRequests("2026-11-03", "2026-11-02")

        compose.runOnIdle { tabSelected.value = true }

        assertSelected("11/02")
        assertOrder("2026-11-02")
        assertRequests("2026-11-03", "2026-11-02", "2026-11-02")
        recordSuccess("hidden-tab-return")
    }

    @Test fun lateResponseCannotReplaceTheNewDateOrders() {
        orders.heldDate = "2026-11-03"
        show()
        select("11/04")
        assertOrder("2026-11-04")

        compose.runOnIdle { orders.completeHeldResponse() }

        assertSelected("11/04")
        assertOrder("2026-11-04")
        compose.onNodeWithText("验证订单2026-11-03").assertDoesNotExist()
        assertRequests("2026-11-03", "2026-11-04")
        recordSuccess("late-response-isolation")
    }

    private fun show() {
        instrumentation.runOnMainSync {
            val model = NursingViewModel(orders)
            store.put("nursing-date-device", model)
            NavigationStateTestActivity.recreatedContent = {
                var context = LocalContext.current
                while (context is ContextWrapper && context !is NavigationStateTestActivity) {
                    context = context.baseContext
                }
                val host = context as NavigationStateTestActivity
                DisposableEffect(host) {
                    host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { host.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }
                LongCareTheme {
                    Box(Modifier.fillMaxSize().background(bgGradientBrush)) {
                        NursingScreen(
                            actions = NursingActions(
                                { error("Device date test must not open a real nursing order") },
                                { error("Device date test must not open a real service") },
                            ),
                            viewModel = model,
                            isTabSelected = tabSelected.value,
                            currentDate = { dateReads.incrementAndGet(); now.get() },
                        )
                    }
                }
            }
        }
        // Both compositions originate at the host's same setContent call site.
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        println("NURSING_DATE_FIXTURE_READY date=${now.get()}")
    }

    private fun select(label: String) {
        compose.onNodeWithText(label).performClick()
        assertSelected(label)
    }

    private fun recreateOn(date: LocalDate) {
        compose.runOnIdle {
            orders.requests.clear()
            now.set(date)
        }
        compose.activityRule.scenario.recreate()
    }

    private fun advanceDateCheck() {
        compose.mainClock.advanceTimeBy(60_001L)
        // The stopped Activity may have no visible Compose root to synchronize against.
        instrumentation.waitForIdleSync()
    }

    private fun assertSelected(label: String) {
        compose.onNode(hasText(label) and isSelected()).assertIsSelected()
    }

    private fun assertOrder(date: String) {
        compose.onNodeWithText("验证订单$date").assertIsDisplayed()
    }

    private fun assertRequests(vararg dates: String) {
        instrumentation.runOnMainSync { assertEquals(dates.toList(), orders.requests.toList()) }
    }

    private fun recordSuccess(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "nursing-date-device")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            File(directory, "$name.png").outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        println("NURSING_DATE_DEVICE_PASS $name requests=${orders.requests.joinToString()}")
    }

    private class RecordingOrders : OrderRepository by forbiddenOrders() {
        val requests = CopyOnWriteArrayList<String>()
        var heldDate: String? = null
        private var heldResponse: Continuation<ApiResult<List<ServiceOrderModel>>>? = null

        override suspend fun getOrderList(daytime: String): ApiResult<List<ServiceOrderModel>> {
            requests += daytime
            return if (daytime == heldDate) suspendCoroutine { heldResponse = it }
            else response(daytime)
        }

        fun completeHeldResponse() {
            val date = checkNotNull(heldDate)
            checkNotNull(heldResponse).resume(response(date))
            heldResponse = null
        }

        fun cancelHeldResponse() {
            heldResponse?.resumeWithException(CancellationException("Device fixture released"))
            heldResponse = null
        }

        private fun response(date: String) = ApiResult.Success(
            listOf(ServiceOrderModel(orderId = 1L, name = "验证订单$date")),
        )
    }

    companion object {
        private fun forbiddenOrders(): OrderRepository = Proxy.newProxyInstance(
            OrderRepository::class.java.classLoader,
            arrayOf(OrderRepository::class.java),
        ) { _, method, _ -> error("Device date test must not call service ${method.name}") } as OrderRepository
    }
}
