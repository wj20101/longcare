package com.ytone.longcare.features.sales

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.navigation.*
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
class SalesEvaluationResultScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun reportOpensOnlyOnClickAndCloseReturnsToResult() {
        val reportUrl = "https://business.invalid/check-result-report"
        compose.setContent {
            LongCareTheme {
                AppNavigationHost(HomeRoute, "sales-test") { nav ->
                    AppEntryProviderBuilder().apply {
                        destination<HomeRoute> {
                            SalesEvaluationCompleteScreen(hasReport = true, grade = "A级",
                                onBack = {}, onDone = {},
                                onOpenReport = { nav.navigateToEvaluationReport(reportUrl, "评估报告") })
                        }
                        destination<WebViewRoute> { entry ->
                            val route = entry.route<WebViewRoute>()
                            assertEquals(reportUrl, route.url)
                            assertFalse(route.isEvaluation)
                            assertFalse(route.showNativeToolbar)
                            Button(onClick = { nav.forEntry(entry.id).popBackStack() }) {
                                Text("关闭测试报告")
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        compose.onNodeWithText("关闭测试报告").assertDoesNotExist()
        compose.onNodeWithText("查看评估报告").performScrollTo().performClick()
        compose.onNodeWithText("关闭测试报告").performClick()
        compose.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        compose.onNodeWithText("查看评估报告").assertIsEnabled()
    }

    @Test fun gradeWithoutReportOffersRefreshButCannotOpenEmptyPage() {
        var refreshes = 0
        compose.setContent {
            LongCareTheme {
                SalesEvaluationCompleteScreen(hasReport = false, grade = "B级", onBack = {}, onDone = {},
                    onOpenReport = { error("Empty report must not open") }, onRefresh = { refreshes++ })
            }
        }
        compose.onNodeWithText("评估报告待同步").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("查看评估报告"))
        compose.onNodeWithText("查看评估报告").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("刷新评估结果"))
        compose.onNodeWithText("刷新评估结果").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun loadingDisablesReportEvenIfPreviousAddressExists() {
        compose.setContent {
            LongCareTheme {
                SalesEvaluationCompleteScreen(hasReport = true, grade = "A级", isLoading = true,
                    onBack = {}, onDone = {}, onOpenReport = { error("Loading") })
            }
        }
        compose.onNodeWithText("查看评估报告").performScrollTo().assertIsNotEnabled()
    }
}
