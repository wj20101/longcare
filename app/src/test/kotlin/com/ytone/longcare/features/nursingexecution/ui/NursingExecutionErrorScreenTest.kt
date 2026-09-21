package com.ytone.longcare.features.nursingexecution.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.nursingexecution.api.NursingExecutionActions
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.UserInfoM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.*
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
class NursingExecutionErrorScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val orders = mockk<OrderRepository>(relaxed = true)
    private val key = OrderKey(42)
    private var backs = 0

    @Test fun dataErrorCanRetryIntoDetailWithMissingOptionalValues() {
        val error = mockk<ApiRequestException>()
        every { error.kind } returns ApiRequestException.Kind.INVALID_RESPONSE
        coEvery { details.getOrderInfo(key, true) } returnsMany listOf(
            ApiResult.Exception(error),
            ApiResult.Success(ServiceOrderInfoModel(orderId = 42, userInfo = UserInfoM(),
                projectList = listOf(ServiceProjectM(projectId = 7))))
        )
        show()
        compose.onNodeWithText("服务数据异常，暂时无法加载护理计划").assertIsDisplayed()
        compose.onNodeWithText("网络异常，请稍后重试").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("护理执行").assertIsDisplayed()
        compose.onNodeWithText("确认信息").assertIsDisplayed()
        compose.onNodeWithText("null", substring = true).assertDoesNotExist()
        compose.onNodeWithText("服务数据异常，暂时无法加载护理计划").assertDoesNotExist()
        // The test clock starts at boot; the existing click guard requires 500 ms uptime.
        compose.runOnIdle {
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
        coVerify(exactly = 2) { details.getOrderInfo(key, true) }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
    }

    @Test fun businessFailureRemainsVisibleAndSystemBackStillWorks() {
        coEvery { details.getOrderInfo(key, true) } returns ApiResult.Failure(4001, "当前计划不可执行")
        show()
        compose.onNodeWithText("当前计划不可执行").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsEnabled()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, backs) }
        coVerify(exactly = 1) { details.getOrderInfo(key, true) }
    }

    private fun show() {
        val vm = SharedOrderDetailViewModel(details, orders, mockk(), mockk(),
            ResourceTextResolver(compose.activity.applicationContext))
        compose.setContent {
            LongCareTheme {
                NursingExecutionScreen(
                    actions = NursingExecutionActions(
                        onNavigateBack = { backs++ },
                        onNavigateToServiceCountdown = { _, _ -> error("Unexpected countdown") },
                        onStartOrderNfcSignIn = { error("Unexpected sign-in") },
                    ),
                    orderKey = key,
                    sharedViewModel = vm,
                )
            }
        }
    }
}
