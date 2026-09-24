package com.ytone.longcare.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServiceCompleteReturnTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var navigator: AppNavigator
    private var homesCreated = 0

    private fun show() {
        compose.setContent {
            LongCareTheme {
                AppNavigationHost(HomeRoute, "service-return") { nav ->
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
    }

    @Test fun everyCompletionExitReturnsToTheSameHomeWithoutServiceHistory() {
        show()
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

    @Test fun countdownExitStillClearsEarlierServiceSteps() {
        show()
        compose.runOnIdle {
            navigator.navigate(SelectServiceRoute(OrderNavParams(7)))
            navigator.navigate(ServiceCountdownRoute(OrderNavParams(7)))
            val page = navigator.forEntry((navigator.backStack.last() as AppNavEntry).id)
            page.navigateToHomeAndClearStack()
            assertEquals(listOf(HomeRoute), navigator.backStack.map { (it as AppNavEntry).route })
        }
        compose.waitForIdle()
    }
}
