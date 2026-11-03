package com.ytone.longcare.features.nursing.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.vm.NursingViewModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NursingScreenDateTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val orders = mockk<OrderRepository>()
    private val active = mutableStateOf(true)
    private val present = mutableStateOf(true)
    private var now = LocalDate(2026, 11, 3)
    private var dateReads = 0
    private val requestedDates = mutableListOf<String>()
    private lateinit var owner: TestOwner
    private lateinit var restoration: StateRestorationTester

    @Test fun visiblePageFollowsTodayWithoutRepeatedOrderRequests() {
        show()
        assertSelected("11/03")
        tick()
        coVerify(exactly = 1) { orders.getOrderList("2026-11-03") }

        compose.runOnIdle { now = LocalDate(2026, 11, 4) }
        tick()

        assertSelected("11/04")
        compose.onNodeWithText("订单2026-11-04").assertIsDisplayed()
        coVerify(exactly = 1) { orders.getOrderList("2026-11-04") }
    }

    @Test fun manualDateSelectionSurvivesMidnightInTheSameMonth() {
        show()
        compose.onNodeWithText("11/02").performClick()
        assertSelected("11/02")
        compose.runOnIdle { now = LocalDate(2026, 11, 4) }
        tick()

        assertSelected("11/02")
        compose.onNodeWithText("订单2026-11-02").assertIsDisplayed()
        coVerify(exactly = 0) { orders.getOrderList("2026-11-04") }
    }

    @Test fun backgroundStopsDateChecksAndResumeHandlesShorterMonth() {
        now = LocalDate(2026, 1, 31)
        show()
        assertSelected("01/31")
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        val readsBefore = dateReads
        compose.runOnIdle { now = LocalDate(2026, 2, 1) }
        tick()
        assertEquals(readsBefore, dateReads)
        coVerify(exactly = 0) { orders.getOrderList("2026-02-01") }

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }

        assertSelected("02/01")
        compose.onNodeWithText("订单2026-02-01").assertIsDisplayed()
        coVerify(exactly = 1) { orders.getOrderList("2026-02-01") }
    }

    @Test fun hiddenTabDoesNotPollAndRefreshesImmediatelyWhenSelected() {
        show()
        compose.runOnIdle { active.value = false }
        val readsBefore = dateReads
        compose.runOnIdle { now = LocalDate(2026, 11, 4) }
        tick()
        assertEquals(readsBefore, dateReads)
        coVerify(exactly = 0) { orders.getOrderList("2026-11-04") }

        compose.runOnIdle { active.value = true }

        assertSelected("11/04")
        compose.onNodeWithText("订单2026-11-04").assertIsDisplayed()
        coVerify(exactly = 1) { orders.getOrderList("2026-11-04") }
    }

    @Test fun reenteringPageInSameProcessUsesNewMonth() {
        now = LocalDate(2026, 12, 31)
        show()
        assertSelected("12/31")
        compose.runOnIdle { present.value = false }
        val readsBefore = dateReads
        compose.runOnIdle { now = LocalDate(2027, 1, 1) }
        tick()
        assertEquals(readsBefore, dateReads)
        compose.runOnIdle { present.value = true }

        assertSelected("01/01")
        compose.onNodeWithText("订单2027-01-01").assertIsDisplayed()
    }

    @Test fun restoringHistoricalSelectionQueriesOnlyTheRestoredDate() {
        show()
        compose.onNodeWithText("11/02").performClick()
        assertSelected("11/02")

        restoreOn(now)

        assertSelected("11/02")
        compose.onNodeWithText("订单2026-11-02").assertIsDisplayed()
        assertRequestedDates("2026-11-02")
    }

    @Test fun restoringAcrossMidnightKeepsHistoricalSelection() {
        show()
        compose.onNodeWithText("11/02").performClick()
        assertSelected("11/02")

        restoreOn(LocalDate(2026, 11, 4))

        assertSelected("11/02")
        compose.onNodeWithText("订单2026-11-02").assertIsDisplayed()
        assertRequestedDates("2026-11-02")
    }

    @Test fun restoringTodayAcrossMidnightFollowsTheNewTodayOnce() {
        show()
        assertSelected("11/03")

        restoreOn(LocalDate(2026, 11, 4))

        assertSelected("11/04")
        compose.onNodeWithText("订单2026-11-04").assertIsDisplayed()
        assertRequestedDates("2026-11-04")
    }

    @Test fun restoringAcrossMonthBoundaryReplacesAnOutOfMonthSelection() {
        now = LocalDate(2026, 1, 31)
        show()
        compose.onNodeWithText("01/30").performClick()
        assertSelected("01/30")

        restoreOn(LocalDate(2026, 2, 1))

        assertSelected("02/01")
        compose.onNodeWithText("订单2026-02-01").assertIsDisplayed()
        assertRequestedDates("2026-02-01")
    }

    @Test fun restoringHiddenTabWaitsUntilSelectedAndKeepsHistoricalDate() {
        show()
        compose.onNodeWithText("11/02").performClick()
        assertSelected("11/02")
        compose.runOnIdle { active.value = false }

        restoreOn(LocalDate(2026, 11, 4))
        assertRequestedDates()
        compose.runOnIdle { active.value = true }

        assertSelected("11/02")
        compose.onNodeWithText("订单2026-11-02").assertIsDisplayed()
        assertRequestedDates("2026-11-02")
    }

    private fun show() {
        coEvery { orders.getOrderList(any()) } coAnswers {
            val date = firstArg<String>()
            requestedDates += date
            ApiResult.Success(listOf(ServiceOrderModel(orderId = 1, name = "订单$date")))
        }
        val viewModel = NursingViewModel(orders)
        compose.runOnIdle {
            owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            Box {
                if (present.value) {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        LongCareTheme {
                            NursingScreen(
                                actions = NursingActions({}, {}),
                                viewModel = viewModel,
                                isTabSelected = active.value,
                                currentDate = { dateReads++; now },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun restoreOn(date: LocalDate) {
        compose.runOnIdle {
            requestedDates.clear()
            now = date
        }
        restoration.emulateSavedInstanceStateRestore()
    }

    private fun assertRequestedDates(vararg dates: String) {
        compose.runOnIdle { assertEquals(dates.toList(), requestedDates) }
    }

    private fun tick() {
        compose.mainClock.advanceTimeBy(60_001L)
        compose.waitForIdle()
    }

    private fun assertSelected(label: String) {
        compose.onNode(hasText(label) and isSelected()).assertIsSelected()
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
