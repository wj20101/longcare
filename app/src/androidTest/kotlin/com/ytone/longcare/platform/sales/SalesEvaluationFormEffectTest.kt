package com.ytone.longcare.platform.sales

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.navigation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SalesEvaluationFormEffectTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()

    @Test fun pendingUrlOpensOnceAndNavigation3BackKeepsHome() {
        val request = mutableStateOf<SalesEvaluationFormRequest?>(null)
        lateinit var navigator: AppNavigator
        var opens = 0
        var releases = 0
        compose.setContent {
            AppNavigationHost(HomeRoute, "mock-sales") { nav ->
                navigator = nav
                AppEntryProviderBuilder().apply {
                    destination<HomeRoute> {
                        Text("评估入口")
                        SalesEvaluationFormEffect(
                            request.value,
                            onLeaveDevice = { releases++ },
                            onOpenForm = { opens++; nav.navigate(WebViewRoute(it, "表单评估")) },
                            onConsumed = { request.value = request.value?.copy(consumed = true) },
                        )
                    }
                    destination<WebViewRoute> { Text("Mock H5 评估页") }
                }
            }
        }
        compose.runOnIdle { assertEquals(0, opens); request.value = SalesEvaluationFormRequest(7, "record") }
        compose.runOnIdle { assertEquals(0, opens); request.value = request.value?.copy(url = "https://mock.internal/form") }
        compose.onNodeWithText("Mock H5 评估页").assertExists()
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(true, releases > 0)
            assertEquals(true, request.value?.consumed)
            navigator.popBackStack()
        }
        compose.onNodeWithText("评估入口").assertExists()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(1, navigator.backStack.size) }
    }

    @Test fun backgroundRequestWaitsForResumeAndDoesNotReopenOnNextResume() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        val request = mutableStateOf(SalesEvaluationFormRequest(7, "record", "https://mock.internal/form"))
        var opens = 0
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SalesEvaluationFormEffect(request.value, {}, { opens++ }, {
                    request.value = request.value.copy(consumed = true)
                })
            }
        }
        compose.runOnIdle { assertEquals(0, opens); owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, opens); owner.lifecycle.currentState = Lifecycle.State.STARTED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, opens); owner.lifecycle.currentState = Lifecycle.State.DESTROYED }
    }
}
