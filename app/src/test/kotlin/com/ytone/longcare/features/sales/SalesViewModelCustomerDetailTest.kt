package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.R
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelCustomerDetailTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `nullable customer detail loads without global blocking`() =
        runTest {
            val detail =
                UserLatentDetailModel(
                    id = 7,
                    userName = "测试客户",
                    guardianName = null,
                    checkTime = null,
                    pgResult = null,
                    pgUrl = null,
                )
            val repository = repositoryWithDetail(ApiResult.Success(detail))
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertEquals(detail, viewModel.uiState.value.selectedCustomer)
            assertEquals(7, viewModel.uiState.value.selectedCustomerId)
            assertNull(viewModel.uiState.value.customerDetailErrorMessage)
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `failed customer detail stays retryable and retry replaces the error`() =
        runTest {
            var attempts = 0
            val recoveredDetail =
                UserLatentDetailModel(
                    id = 7,
                    userName = "已恢复客户",
                )
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getUserLatentDetail(7) } answers {
                        if (attempts++ == 0) {
                            ApiResult.Failure(
                                code = 500,
                                message = "客户服务繁忙",
                            )
                        } else {
                            ApiResult.Success(recoveredDetail)
                        }
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertNull(viewModel.uiState.value.selectedCustomer)
            assertEquals(
                "客户服务繁忙",
                viewModel.uiState.value.customerDetailErrorMessage,
            )
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.retryCustomerDetail()

            assertEquals(2, attempts)
            assertEquals(recoveredDetail, viewModel.uiState.value.selectedCustomer)
            assertNull(viewModel.uiState.value.customerDetailErrorMessage)
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
        }

    @Test
    fun `mismatched customer id is rejected instead of showing another customer`() =
        runTest {
            val repository =
                repositoryWithDetail(
                    ApiResult.Success(
                        UserLatentDetailModel(
                            id = 8,
                            userName = "其他客户",
                        )
                    )
                )
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertNull(viewModel.uiState.value.selectedCustomer)
            assertEquals(
                "客户详情数据异常，请重试",
                viewModel.uiState.value.customerDetailErrorMessage,
            )
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
        }

    @Test
    fun `completed SDK evaluation queries report from check result rather than customer detail`() =
        runTest {
            val serviceReportUrl = "https://care.example.com/assessment/report/7"
            val repository =
                repositoryWithDetail(
                    ApiResult.Success(
                        UserLatentDetailModel(
                            id = 7,
                            userName = "测试客户",
                            pgUrl = serviceReportUrl,
                        )
                    )
                )
            val viewModel = createViewModel(repository)

            viewModel.selectCustomer(7)
            viewModel.prepareEvaluation(7)
            coEvery { repository.getCheckResult(7, "sdk-record") } returns ApiResult.Success(
                com.ytone.longcare.model.CheckResultModel("A级", serviceReportUrl),
            )
            viewModel.onSdkEvent(
                QlzSdkEvent.Completed(
                    recordId = "sdk-record",
                )
            )
            advanceUntilIdle()

            viewModel.loadEvaluationResult()
            advanceUntilIdle()
            assertEquals(serviceReportUrl, viewModel.uiState.value.evaluationResult?.pgUrl)
            coVerify(exactly = 1) { repository.getCheckResult(7, "sdk-record") }
            coVerify(exactly = 0) { repository.getUserLatentDetail(7) }
        }

    private fun repositoryWithDetail(
        result: ApiResult<UserLatentDetailModel>,
    ): SaleRepository =
        mockk<SaleRepository>(relaxed = true) {
            coEvery { getRecentUserLatentList() } returns
                ApiResult.Success(emptyList())
            coEvery { getUserLatentDetail(any()) } returns result
        }

    @Test
    fun `restored customer detail reloads saved id only once`() = runTest {
        val detail = UserLatentDetailModel(id = 7, pgResult = "A级")
        val repository = repositoryWithDetail(ApiResult.Success(detail))
        val originalHandle = SavedStateHandle()
        createViewModel(repository, originalHandle).loadCustomerDetail(7)
        val restoredHandle = SavedStateHandle(originalHandle.keys().associateWith { originalHandle.get<Any?>(it) })
        val restored = createViewModel(repository, restoredHandle)
        assertNull(restored.uiState.value.selectedCustomer)

        restored.restoreCustomerDetailIfNeeded()
        restored.restoreCustomerDetailIfNeeded()
        advanceUntilIdle()

        assertEquals(detail, restored.uiState.value.selectedCustomer)
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `restoring detail does not duplicate pending request`() = runTest {
        val response = CompletableDeferred<ApiResult<UserLatentDetailModel>>()
        val repository = repositoryWithDetail(ApiResult.Success(UserLatentDetailModel(id = 7)))
        coEvery { repository.getUserLatentDetail(7) } coAnswers { response.await() }
        val vm = createViewModel(repository, SavedStateHandle(mapOf("sales.evaluation.customer" to 7)))
        repeat(2) { vm.restoreCustomerDetailIfNeeded() }
        response.complete(ApiResult.Success(UserLatentDetailModel(id = 7)))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `restored detail failure stays retryable without automatic retry loop`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Failure(code = 500, message = "暂不可用"))
        val vm = createViewModel(repository, SavedStateHandle(mapOf("sales.evaluation.customer" to 7)))
        repeat(2) { vm.restoreCustomerDetailIfNeeded() }
        advanceUntilIdle()
        assertEquals("暂不可用", vm.uiState.value.customerDetailErrorMessage)
        coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `detail restoration without saved customer does not request id zero`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Success(UserLatentDetailModel(id = 7)))
        createViewModel(repository).restoreCustomerDetailIfNeeded()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    private fun createViewModel(
        repository: SaleRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): SalesViewModel {
        val applicationContext =
            mockk<Context>(relaxed = true) {
                every { getString(R.string.sales_error_customer_detail_data) } returns
                    "客户详情数据异常，请重试"
            }
        return SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = UnusedPhotoCloudUploader,
            imagePipeline = testImagePipeline(applicationContext),
            evaluationDeviceGateway = mockk<SalesEvaluationDeviceGateway>(relaxed = true),
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            savedStateHandle = savedStateHandle,
            textResolver = ResourceTextResolver(applicationContext),
        )
    }
}
