package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.R
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.domain.cos.repository.CosRepository
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    fun `customer photo keys resolve with sales permissions without blocking detail`() = runTest {
        val detail = UserLatentDetailModel(id = 7, img1 = "sale/one.jpg", img2 = "", img3 = "sale/three.jpg")
        val pending = CompletableDeferred<String>()
        val cos = mockk<CosRepository> {
            coEvery { getFileUrl("sale/one.jpg", 15, null) } coAnswers { pending.await() }
            coEvery { getFileUrl("sale/three.jpg", 15, null) } returns "https://images.invalid/three"
        }
        val viewModel = createViewModel(repositoryWithDetail(ApiResult.Success(detail)), cosRepository = cos)
        viewModel.loadCustomerDetail(7)
        assertEquals(detail, viewModel.uiState.value.selectedCustomer)
        assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
        assertEquals(listOf("sale/one.jpg", "sale/three.jpg"), viewModel.uiState.value.customerPhotos.map { it.key })
        assertEquals(true, viewModel.uiState.value.customerPhotos[0].isLoading)
        assertEquals("https://images.invalid/three", viewModel.uiState.value.customerPhotos[1].url)
        pending.complete("https://images.invalid/one")
        advanceUntilIdle()
        assertEquals("https://images.invalid/one", viewModel.uiState.value.customerPhotos[0].url)
        coVerify(exactly = 1) { cos.getFileUrl("sale/one.jpg", 15, null) }
        coVerify(exactly = 1) { cos.getFileUrl("sale/three.jpg", 15, null) }
    }

    @Test
    fun `retrying a failed photo keeps the customer and other photos`() = runTest {
        val detail = UserLatentDetailModel(id = 7, img1 = "sale/one.jpg", img2 = "sale/two.jpg")
        var attempts = 0
        val cos = mockk<CosRepository> {
            coEvery { getFileUrl("sale/one.jpg", 15, null) } returns "https://images.invalid/one"
            coEvery { getFileUrl("sale/two.jpg", 15, null) } coAnswers {
                if (attempts++ == 0) throw java.io.IOException("unavailable")
                "https://images.invalid/two"
            }
        }
        val repository = repositoryWithDetail(ApiResult.Success(detail))
        val viewModel = createViewModel(repository, cosRepository = cos)
        viewModel.loadCustomerDetail(7)
        assertEquals("照片加载失败", viewModel.uiState.value.customerPhotos[1].errorMessage)
        assertEquals(detail, viewModel.uiState.value.selectedCustomer)
        assertNull(viewModel.uiState.value.customerDetailErrorMessage)
        viewModel.retryCustomerPhoto("sale/two.jpg")
        advanceUntilIdle()
        assertEquals("https://images.invalid/two", viewModel.uiState.value.customerPhotos[1].url)
        assertNull(viewModel.uiState.value.customerPhotos[1].errorMessage)
        coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
        coVerify(exactly = 1) { cos.getFileUrl("sale/one.jpg", 15, null) }
        coVerify(exactly = 2) { cos.getFileUrl("sale/two.jpg", 15, null) }
    }

    @Test
    fun `late photo response cannot replace a different customers photos`() = runTest {
        val late = CompletableDeferred<String>()
        val repository = mockk<SaleRepository> {
            coEvery { getUserLatentDetail(7) } returns ApiResult.Success(UserLatentDetailModel(id = 7, img1 = "sale/old.jpg"))
            coEvery { getUserLatentDetail(8) } returns ApiResult.Success(UserLatentDetailModel(id = 8, img1 = "sale/new.jpg"))
        }
        val cos = mockk<CosRepository> {
            coEvery { getFileUrl("sale/old.jpg", 15, null) } coAnswers { withContext(NonCancellable) { late.await() } }
            coEvery { getFileUrl("sale/new.jpg", 15, null) } returns "https://images.invalid/new"
        }
        val viewModel = createViewModel(repository, cosRepository = cos)
        viewModel.loadCustomerDetail(7)
        viewModel.loadCustomerDetail(8)
        late.complete("https://images.invalid/old")
        advanceUntilIdle()
        assertEquals(8, viewModel.uiState.value.selectedCustomer?.id)
        assertEquals(listOf("sale/new.jpg"), viewModel.uiState.value.customerPhotos.map { it.key })
        assertEquals("https://images.invalid/new", viewModel.uiState.value.customerPhotos.single().url)
    }

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
    fun `restored customer detail reloads route id`() = runTest {
        val detail = UserLatentDetailModel(id = 7, pgResult = "A级")
        val repository = repositoryWithDetail(ApiResult.Success(detail))
        val originalHandle = SavedStateHandle()
        createViewModel(repository, originalHandle).loadCustomerDetail(7)
        val restoredHandle = SavedStateHandle(originalHandle.keys().associateWith { originalHandle.get<Any?>(it) })
        val restored = createViewModel(repository, restoredHandle)
        assertNull(restored.uiState.value.selectedCustomer)

        restored.loadCustomerDetail(7)
        advanceUntilIdle()

        assertEquals(detail, restored.uiState.value.selectedCustomer)
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `refreshing detail replaces cached evaluation result`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Success(UserLatentDetailModel(id = 7)))
        val vm = createViewModel(repository)
        vm.loadCustomerDetail(7)
        coEvery { repository.getUserLatentDetail(7) } returns ApiResult.Success(UserLatentDetailModel(id = 7, pgResult = "B级"))
        vm.loadCustomerDetail(7)
        advanceUntilIdle()
        assertEquals("B级", vm.uiState.value.selectedCustomer?.pgResult)
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `restored detail failure stays retryable without automatic retry loop`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Failure(code = 500, message = "暂不可用"))
        val vm = createViewModel(repository, SavedStateHandle(mapOf("sales.evaluation.customer" to 7)))
        vm.loadCustomerDetail(7)
        advanceUntilIdle()
        assertEquals("暂不可用", vm.uiState.value.customerDetailErrorMessage)
        coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
    }

    @Test
    fun `invalid route customer does not request id zero`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Success(UserLatentDetailModel(id = 7)))
        createViewModel(repository).loadCustomerDetail(0)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    @Test fun `separate detail entry never overwrites retained result customer or record`() = runTest {
        val repository = repositoryWithDetail(ApiResult.Success(UserLatentDetailModel(id = 8)))
        coEvery { repository.getCheckResult(7, "record-7") } returns
            ApiResult.Success(com.ytone.longcare.model.CheckResultModel(pgResult = "A级", pgUrl = "https://report.invalid"))
        val resultHandle = SavedStateHandle()
        val result = createViewModel(repository, resultHandle)
        result.showEvaluationResult(7, "record-7")
        result.loadEvaluationResult()
        val detail = createViewModel(repository)
        detail.loadCustomerDetail(8)
        advanceUntilIdle()
        assertEquals(8, detail.uiState.value.selectedCustomer?.id)
        assertEquals(7, result.uiState.value.selectedCustomerId)
        assertEquals("record-7", result.uiState.value.evaluationRecordId)
        assertEquals("A级", result.uiState.value.evaluationResult?.pgResult)
        result.showEvaluationResult(7, "record-7")
        assertEquals("A级", result.uiState.value.evaluationResult?.pgResult)
        val restored = createViewModel(repository, SavedStateHandle(
            resultHandle.keys().associateWith { resultHandle.get<Any?>(it) }))
        restored.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("record-7", restored.uiState.value.evaluationRecordId)
        assertEquals("A级", restored.uiState.value.evaluationResult?.pgResult)
    }

    private fun createViewModel(
        repository: SaleRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        cosRepository: CosRepository = mockk(relaxed = true),
    ): SalesViewModel {
        val applicationContext =
            mockk<Context>(relaxed = true) {
                every { getString(R.string.sales_error_customer_detail_data) } returns
                    "客户详情数据异常，请重试"
                every { getString(R.string.sales_customer_photo_load_failed) } returns "照片加载失败"
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
            cosRepository = cosRepository,
        )
    }
}
