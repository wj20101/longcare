package com.ytone.longcare.features.sales

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.integration.qlz.QlzEvaluationUiState
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.*
import com.ytone.longcare.platform.sales.SalesSdkUiController
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
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
class SalesFlowScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var navigator: AppNavigator
    private val models = mutableMapOf<String, SalesViewModel>()
    private val images = mockk<com.ytone.longcare.common.image.UnifiedImagePipeline>(relaxed = true)
    private val repository = mockk<SaleRepository>(relaxed = true) {
        coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
        coEvery { getCheckResult(7, "record-7") } returns ApiResult.Success(CheckResultModel("A级", "https://report.invalid"))
        coEvery { getUserLatentDetail(any()) } answers { ApiResult.Success(UserLatentDetailModel(id = firstArg(), userName = "目标客户")) }
    }

    private fun show(initial: SalesRoute) {
        val home = mockk<HomeSharedViewModel> { every { userState } returns MutableStateFlow(null) }
        compose.setContent {
            LongCareTheme {
                AppNavigationHost(HomeRoute, "offline-sales") { nav ->
                    navigator = nav
                    AppEntryProviderBuilder().apply {
                        destination<HomeRoute> { Text("原生来源") }
                        destination<SalesRoute> { entry ->
                            val vm: SalesViewModel = viewModel {
                                SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
                                    images, mockk(relaxed = true), mockk(relaxed = true),
                                    ResourceTextResolver(compose.activity), SavedStateHandle())
                            }
                            SideEffect { models[entry.id] = vm }
                            val controller = remember {
                                mockk<SalesSdkUiController>(relaxed = true) {
                                    every { uiState } returns MutableStateFlow(QlzEvaluationUiState())
                                    every { requiredRuntimePermissions() } returns emptyArray()
                                }
                            }
                            val page = nav.forEntry(entry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
                            SalesExperienceScreen(homeActions(page, entry), home, page, entry.route(), vm, controller)
                        }
                        destination<WebViewRoute> { Text("离线报告") }
                    }
                }
            }
        }
        compose.runOnIdle { navigator.navigate(initial) }
    }

    private fun routes() = navigator.backStack.map { (it as AppNavEntry).route }
    private fun top() = navigator.backStack.last() as AppNavEntry
    private fun click(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }

    @Test fun submissionFailureRetriesThenSdkResultAndH5DetailRetainNormalBackStack() {
        val requests = mutableListOf<AddUserLatentParamModel>()
        coEvery { repository.addUserLatent(capture(requests)) } returnsMany listOf(
            ApiResult.Failure(500, "提交暂不可用"),
            ApiResult.Success(AddUserLatentResultModel(7, "https://form.invalid")),
        )
        show(SalesRoute(SalesPage.REGISTRATION, draft = SalesCustomerDraft(userName = "离线测试", liveAddress = "地址")))
        click("提交")
        compose.runOnIdle { assertEquals(listOf(HomeRoute, SalesRoute(SalesPage.REGISTRATION_CONFIRM,
            draft = SalesCustomerDraft(userName = "离线测试", liveAddress = "地址"))), routes()) }
        click("确定提交")
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(SalesPage.REGISTRATION_CONFIRM, (top().route as SalesRoute).page) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("离线测试"))
        compose.onNodeWithText("离线测试").assertExists()
        click("确定提交")
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, requests.size)
            assertEquals(requests[0], requests[1])
            assertEquals(SalesPage.SUBMIT_SUCCESS, (top().route as SalesRoute).page)
            assertEquals("地址", (top().route as SalesRoute).address)
            assertNull((top().route as SalesRoute).draft)
        }
        click("进行评估")
        click("设备自动评估")
        compose.runOnIdle { models.getValue(top().id).onSdkEvent(QlzSdkEvent.Completed("record-7")) }
        compose.waitForIdle()
        compose.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        val result = top()
        click("确认并提交评估结果")
        compose.runOnIdle { navigator.forEntry(top().id).openCustomerDetailsFromH5(8) }
        compose.waitForIdle()
        compose.onNodeWithText("目标客户").assertExists()
        compose.runOnIdle {
            val resultVm = models.getValue(result.id)
            assertEquals(7, resultVm.uiState.value.selectedCustomerId)
            assertEquals("record-7", resultVm.uiState.value.evaluationRecordId)
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        click("完成")
        compose.runOnIdle {
            assertEquals(SalesPage.EVALUATION_CHOICE, (top().route as SalesRoute).page)
            assertFalse(routes().filterIsInstance<SalesRoute>().any { it.page in setOf(
                SalesPage.REGISTRATION, SalesPage.REGISTRATION_CONFIRM, SalesPage.DEVICE_STATUS, SalesPage.EVALUATION_COMPLETE) })
            assertFalse(routes().any { it is WebViewRoute })
        }
    }

    @Test fun confirmationBackClosesRatherThanReopeningForm() {
        show(SalesRoute(SalesPage.REGISTRATION, draft = SalesCustomerDraft(userName = "离线测试")))
        click("提交")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("原生来源").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(HomeRoute), routes()) }
        coVerify(exactly = 0) { repository.addUserLatent(any()) }
    }

    @Test fun leavingConfirmationReleasesItsPhotosEvenWhenEntryIsRemoved() {
        show(SalesRoute(SalesPage.REGISTRATION_CONFIRM, draft = SalesCustomerDraft(userName = "离线测试"),
            photos = listOf("content://offline/photo")))
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("原生来源").assertIsDisplayed()
        coVerify(exactly = 1) { images.deleteManagedImages(match { it.single().toString() == "content://offline/photo" }) }
        coVerify(exactly = 0) { repository.addUserLatent(any()) }
    }

    @Test fun completedEvaluationRefreshesOriginalDetailWhenReturningThroughChoice() {
        show(SalesRoute(SalesPage.CUSTOMER_DETAIL, 7))
        compose.onNodeWithText("目标客户").assertExists()
        val detail = top()
        click("立即评估")
        click("设备自动评估")
        compose.runOnIdle { models.getValue(top().id).onSdkEvent(QlzSdkEvent.Completed("record-7")) }
        compose.onNodeWithText("评估成功，评估等级为：A级").assertExists()
        coEvery { repository.getUserLatentDetail(7) } returns ApiResult.Success(
            UserLatentDetailModel(id = 7, userName = "目标客户", pgResult = "B级", pgUrl = "https://report.invalid"))
        click("完成")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(detail, top())
            assertEquals("B级", models.getValue(detail.id).uiState.value.selectedCustomer?.pgResult)
        }
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
        // Recomposition alone must not reload the detail.
        compose.runOnIdle { models.getValue(detail.id).clearTransientMessage() }
        compose.waitForIdle()
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
    }

    @Test fun sameCustomerH5ReusesDetailAndRefreshFailureCanBeRetried() {
        show(SalesRoute(SalesPage.CUSTOMER_DETAIL, 7))
        compose.onNodeWithText("目标客户").assertExists()
        val detail = top()
        compose.runOnIdle { navigator.forEntry(detail.id).navigateToEvaluationReport("https://report.invalid", "报告") }
        compose.onNodeWithText("离线报告").assertExists()
        coEvery { repository.getUserLatentDetail(7) } returns ApiResult.Failure(500, "详情暂不可用")
        compose.runOnIdle { navigator.forEntry(top().id).openCustomerDetailsFromH5(7) }
        compose.onNodeWithText("详情暂不可用").assertExists()
        compose.runOnIdle { assertEquals(detail, top()) }
        coEvery { repository.getUserLatentDetail(7) } returns ApiResult.Success(UserLatentDetailModel(id = 7, userName = "更新客户"))
        compose.onNodeWithTag("customer_detail_retry").performClick()
        compose.onNodeWithText("更新客户").assertExists()
        coVerify(exactly = 3) { repository.getUserLatentDetail(7) }
    }

    @Test fun returningToCustomerListRetainsLoadedPagesAndScrollPosition() {
        coEvery { repository.searchUserLatentList(any()) } answers {
            val page = firstArg<SearchUserLatentParamModel>().pageIndex
            ApiResult.Success(if (page <= 2) ((page - 1) * 20 + 1..page * 20).map {
                UserLatentListModel(id = it, userName = "列表客户$it")
            } else emptyList())
        }
        show(SalesRoute(SalesPage.CUSTOMERS))
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val list = top()
        compose.runOnIdle { models.getValue(list.id).loadNextCustomerPage() }
        compose.waitForIdle()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(24)
        compose.onNodeWithText("列表客户25").assertIsDisplayed().performClick()
        compose.onNodeWithText("目标客户").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("列表客户25").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(list, top())
            assertEquals(40, models.getValue(list.id).uiState.value.customers.size)
            assertEquals(2, models.getValue(list.id).uiState.value.customerPageIndex)
        }
        coVerify(exactly = 1) { repository.searchUserLatentList(match { it.pageIndex == 1 }) }
        coVerify(exactly = 1) { repository.searchUserLatentList(match { it.pageIndex == 2 }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("新条件")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        coVerify(exactly = 1) { repository.searchUserLatentList(match { it.pageIndex == 1 && it.userName == "新条件" }) }
        compose.runOnIdle { assertEquals(1, models.getValue(list.id).uiState.value.customerPageIndex) }
        compose.onNodeWithText("列表客户1").assertIsDisplayed()
    }

    @Test fun emptyCustomerListIsRetainedButFailedSearchCanRetry() {
        coEvery { repository.searchUserLatentList(any()) } returnsMany listOf(
            ApiResult.Failure(500, "查询失败"), ApiResult.Success(emptyList()))
        show(SalesRoute(SalesPage.CUSTOMERS))
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.waitForIdle()
        val list = top()
        compose.runOnIdle { navigator.forEntry(list.id).navigateToWebView("https://offline.invalid", "临时网页") }
        compose.onNodeWithText("离线报告").assertExists()
        compose.runOnIdle { navigator.forEntry(top().id).popBackStack() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, models.getValue(list.id).uiState.value.customerPageIndex)
            assertTrue(models.getValue(list.id).uiState.value.customers.isEmpty())
        }
        coVerify(exactly = 2) { repository.searchUserLatentList(any()) }
    }
}
