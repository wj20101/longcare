package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import com.ytone.longcare.core.ui.R as CoreUiR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceCountdownLocationRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val permissionGranted = AtomicBoolean(true)
    private val locationEnabled = AtomicBoolean(true)
    private val countdownState = MutableStateFlow(ServiceCountdownState.RUNNING)
    private val location = mockk<LocationTrackingViewModel>(relaxed = true)
    private val order = OrderKey(42, 7)

    private val recoveryLabel get() = compose.activity.getString(R.string.service_countdown_enable_location)
    private val permissionMessage get() = compose.activity.getString(R.string.service_countdown_location_permission_required)

    @After
    fun cleanUp() {
        unmockkObject(UnifiedPermissionHelper)
    }

    @Test
    fun `external permission revocation exposes an explicit recovery action`() {
        render()
        compose.onNodeWithText(recoveryLabel).assertDoesNotExist()

        returnToPage { permissionGranted.set(false) }

        compose.onNodeWithText(permissionMessage).assertDoesNotExist()
        compose.onNodeWithText(recoveryLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText(permissionMessage).assertIsDisplayed()
        verify(exactly = 1) { location.startTracking(order) }
    }

    @Test
    fun `dismissing location guidance keeps manual recovery without repeated prompts`() {
        permissionGranted.set(false)
        render()
        compose.onNodeWithText(permissionMessage).assertIsDisplayed()
        dismissGuidance()

        repeat(2) { returnToPage() }

        compose.onNodeWithText(permissionMessage).assertDoesNotExist()
        compose.onNodeWithText(recoveryLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText(permissionMessage).assertIsDisplayed()
        verify(exactly = 0) { location.startTracking(any()) }
    }

    @Test
    fun `restored permission hides recovery and resumes tracking`() {
        permissionGranted.set(false)
        render()
        dismissGuidance()
        compose.onNodeWithText(recoveryLabel).assertIsDisplayed()

        returnToPage { permissionGranted.set(true) }

        compose.onNodeWithText(recoveryLabel).assertDoesNotExist()
        compose.onNodeWithText(permissionMessage).assertDoesNotExist()
        verify(exactly = 1) { location.startTracking(order) }
    }

    @Test
    fun `restored page rechecks location without replaying initial guidance`() {
        val restoration = StateRestorationTester(compose)
        render(restoration)
        compose.onNodeWithText(recoveryLabel).assertDoesNotExist()

        permissionGranted.set(false)
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(recoveryLabel).assertIsDisplayed()
        compose.onNodeWithText(permissionMessage).assertDoesNotExist()
    }

    @Test
    fun `disabled location setting uses the existing location settings guidance`() {
        render()

        returnToPage { locationEnabled.set(false) }

        compose.onNodeWithText(recoveryLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText(compose.activity.getString(CoreUiR.string.location_service_open_settings)).assertIsDisplayed()
        verify(exactly = 1) { location.startTracking(order) }
    }

    @Test
    fun `ended service does not expose recovery or restart location`() {
        render()
        compose.runOnIdle { countdownState.value = ServiceCountdownState.ENDED }

        returnToPage { permissionGranted.set(false) }

        compose.onNodeWithText(recoveryLabel).assertDoesNotExist()
        verify(exactly = 1) { location.startTracking(order) }
    }

    private fun dismissGuidance() {
        compose.runOnIdle { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        compose.onNodeWithText(compose.activity.getString(R.string.service_countdown_permission_later)).performClick()
        compose.onNodeWithText(permissionMessage).assertDoesNotExist()
    }

    private fun returnToPage(change: () -> Unit = {}) {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        change()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
    }

    private fun render(restoration: StateRestorationTester? = null) {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        mockkObject(UnifiedPermissionHelper)
        every { UnifiedPermissionHelper.hasLocationPermission(any()) } answers { permissionGranted.get() }
        every { UnifiedPermissionHelper.isLocationServiceEnabled(any()) } answers { locationEnabled.get() }
        val countdown = mockk<ServiceCountdownViewModel>(relaxed = true)
        every { countdown.countdownState } returns countdownState
        every { countdown.formattedTime } returns MutableStateFlow("30:00")
        every { countdown.orderStateError } returns MutableStateFlow<ServiceOrderStateModel?>(null)
        every { countdown.canScheduleExactAlarms() } returns true
        every { countdown.canUseFullScreenIntent() } returns true
        every { countdown.synchronizeCountdownSession(any(), any()) } returns CompletableDeferred(true)
        val details = mockk<SharedOrderDetailViewModel>(relaxed = true)
        every { details.getCachedOrderInfo(any()) } returns null
        val actions = ServiceCountdownActions({}, { _, _, _ -> }, {}, MutableStateFlow(null), {})
        val content: @Composable () -> Unit = {
            LongCareTheme {
                ServiceCountdownScreen(actions, order, emptyList(), details, countdown, location)
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }
}
