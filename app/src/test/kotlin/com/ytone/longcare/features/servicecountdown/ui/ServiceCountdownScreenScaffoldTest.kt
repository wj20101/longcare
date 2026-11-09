package com.ytone.longcare.features.servicecountdown.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ytone.longcare.R
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceCountdownScreenScaffoldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun countdown_toolbar_omits_background_settings_before_and_after_restoration() {
        val details = mockk<SharedOrderDetailViewModel>()
        every { details.getCachedOrderInfo(any()) } returns null
        var backClicks = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LongCareTheme {
                ServiceCountdownScreenScaffold(
                    countdownState = ServiceCountdownState.RUNNING,
                    formattedTime = "30:00",
                    orderKey = OrderKey(42, 7),
                    projectIdList = emptyList(),
                    sharedViewModel = details,
                    onNavigateBack = { backClicks++ },
                    onOpenPhotoUpload = {},
                    onBottomActionClick = {},
                )
            }
        }
        val title = compose.activity.getString(R.string.service_countdown_title)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("后台设置").assertDoesNotExist()
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("后台设置").assertDoesNotExist()
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        // Move past the existing 500 ms click debounce on Robolectric's clock.
        compose.runOnIdle { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backClicks) }
    }
}
