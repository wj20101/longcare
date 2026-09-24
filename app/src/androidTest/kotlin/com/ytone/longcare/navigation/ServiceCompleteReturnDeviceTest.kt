package com.ytone.longcare.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Offline test with the real completion screen and Navigation 3 entry lifecycle. */
class ServiceCompleteReturnDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()

    @Test fun allCompletionExitsPreserveTheOriginalHome() {
        lateinit var navigator: AppNavigator
        var homesCreated = 0
        compose.setContent {
            LongCareTheme {
                AppNavigationHost(HomeRoute, "offline-completion") { nav ->
                    navigator = nav
                    AppEntryProviderBuilder().apply {
                        destination<HomeRoute> {
                            val state = rememberSaveable { mutableStateOf(++homesCreated) }
                            Text("首页状态 ${state.value}")
                        }
                        registerServiceCompleteRoute(nav)
                    }
                }
            }
        }
        compose.waitForIdle()
        val home = navigator.backStack.single()
        repeat(3) { exit ->
            compose.runOnIdle {
                navigator.navigate(SelectServiceRoute(OrderNavParams(7)))
                navigator.completeService(ServiceCompleteRoute(OrderNavParams(7), ServiceCompleteData()))
            }
            compose.waitForIdle()
            when (exit) {
                0 -> compose.onNodeWithContentDescription("返回").performClick()
                1 -> compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                else -> compose.onNodeWithText("完成").performClick()
            }
            compose.onNodeWithText("首页状态 1").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf(home), navigator.backStack) }
        }
        assertEquals(1, homesCreated)
    }
}
