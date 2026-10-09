package com.ytone.longcare.features.sales

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentDetailModel
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesCustomerDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialState_rendersLoadingBeforeTheRequestStarts() {
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer = null,
                    isLoading = false,
                    errorMessage = null,
                    onBack = {}, onRetry = {}, onEvaluate = {}, onOpenReport = {},
                )
            }
        }
        composeRule.onNodeWithText("正在加载客户信息…").assertIsDisplayed()
        composeRule.onNodeWithText("暂无客户信息").assertDoesNotExist()
    }

    @Test
    fun loadingState_isRenderedInsideTheDetailPage() {
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer = null,
                    isLoading = true,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = {},
                    onOpenReport = {},
                )
            }
        }

        composeRule.onNodeWithText("客户详情").assertIsDisplayed()
        composeRule.onNodeWithText("正在加载客户信息…").assertIsDisplayed()
    }

    @Test
    fun errorState_keepsTheDetailPageRetryable() {
        val retries = AtomicInteger(0)
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer = null,
                    isLoading = false,
                    errorMessage = "客户服务繁忙",
                    onBack = {},
                    onRetry = { retries.incrementAndGet() },
                    onEvaluate = {},
                    onOpenReport = {},
                )
            }
        }

        composeRule.onNodeWithText("客户信息加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("客户服务繁忙").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()

        assertEquals(1, retries.get())
    }

    @Test
    fun nullableFields_doNotPreventCustomerActionsFromRendering() {
        val evaluatedCustomerId = AtomicInteger(0)
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer =
                        UserLatentDetailModel(
                            id = 7,
                            userName = null,
                            identityCardNumber = null,
                            guardianName = null,
                            guardianPhone = null,
                            guardianRelation = null,
                            liveAddress = null,
                            pgResult = null,
                            pgUrl = null,
                        ),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = evaluatedCustomerId::set,
                    onOpenReport = {},
                )
            }
        }

        composeRule.onNodeWithText("客户信息").assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("未评估"))
        composeRule.onNodeWithText("未评估").assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("立即评估"))
        composeRule.onNodeWithText("立即评估").performClick()

        assertEquals(7, evaluatedCustomerId.get())
    }

    @Test
    fun photoFailure_keepsDetailVisibleAndRetriesTheSelectedImage() {
        var retriedKey: String? = null
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer = UserLatentDetailModel(id = 7, userName = "测试客户", img1 = "sale/one.jpg", img2 = "sale/two.jpg"),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = {},
                    onOpenReport = {},
                    photos = listOf(
                        SalesCustomerPhotoUiState("sale/one.jpg", isLoading = false, errorMessage = "照片加载失败"),
                        SalesCustomerPhotoUiState("sale/two.jpg", isLoading = false, errorMessage = "照片加载失败"),
                    ),
                    onRetryPhoto = { retriedKey = it },
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("customer_photo_1"))
        composeRule.onNodeWithText("登记照片").assertIsDisplayed()
        composeRule.onAllNodesWithText("照片加载失败")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("重试")[1].performClick()
        assertEquals("sale/two.jpg", retriedKey)
        composeRule.onNodeWithText("客户信息加载失败").assertDoesNotExist()
    }

    @Test
    fun disabilityAndRemarks_areDisplayedFromCustomerDetail() {
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer = UserLatentDetailModel(
                        id = 7,
                        userName = "测试客户",
                        isDisability = 1,
                        remarks = "本次API联调备注",
                    ),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = {},
                    onOpenReport = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("是否残疾："))
        composeRule.onNodeWithText("是否残疾：").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("是"))
        composeRule.onNodeWithText("是").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("本次API联调备注"))
        composeRule.onNodeWithText("本次API联调备注").assertIsDisplayed()
    }

    @Test
    fun declarationRow_usesTheDetailReviewStatus() {
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer =
                        UserLatentDetailModel(
                            id = 7,
                            checkStatus = UserLatentCheckState.REJECTED,
                        ),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = {},
                    onOpenReport = {},
                )
            }
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("申报："))
        composeRule.onNodeWithText("申报：").assertIsDisplayed()
        composeRule.onNodeWithText("审核被拒绝").assertIsDisplayed()
    }

    @Test
    fun evaluatedCustomer_showsOnlyTheResultContent() {
        composeRule.setContent {
            SalesPageBackground {
                SalesCustomerDetailScreen(
                    customer =
                        UserLatentDetailModel(
                            id = 7,
                            pgId = 9,
                            pgResult = "重度失能",
                        ),
                    isLoading = false,
                    errorMessage = null,
                    onBack = {},
                    onRetry = {},
                    onEvaluate = {},
                    onOpenReport = {},
                )
            }
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("评估："))
        composeRule.onNodeWithText("评估：").assertIsDisplayed()
        composeRule.onNodeWithText("重度失能").assertIsDisplayed()
        composeRule.onNodeWithText("已评估", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("评估结果", substring = true).assertDoesNotExist()
    }
}
