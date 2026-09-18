package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzDeviceOption
import com.ytone.longcare.integration.qlz.QlzEvaluationDriver
import com.ytone.longcare.integration.qlz.QlzEvaluationDriverEvent
import com.ytone.longcare.integration.qlz.QlzEvaluationDriverFactory
import com.ytone.longcare.integration.qlz.QlzEvaluationSession
import com.ytone.longcare.integration.qlz.QlzEvaluationUploadContext
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.CheckResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Exercises the real ViewModel/session handoff with entirely in-memory external boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalesMockEvaluationFlowTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `mock upload creates pending H5 request using refreshed business URL`() = runTest {
        val viewModel = completeEvaluation(
            ApiResult.Success(UserLatentDetailModel(id = 7, userName = "Mock 客户", pgUrl = BUSINESS_REPORT))
        )
        assertEquals(BUSINESS_REPORT, viewModel.uiState.value.selectedCustomer?.pgUrl)
        assertEquals("mock-record", viewModel.uiState.value.evaluationFormRequest?.recordId)
        assertEquals(viewModel.uiState.value.selectedCustomer?.pgUrl, viewModel.uiState.value.evaluationFormRequest?.url)
    }

    @Test fun `mock upload then H5 close reaches result page and finishing clears flow`() = runTest {
        val vm = completeEvaluation(
            ApiResult.Success(UserLatentDetailModel(id = 7, pgUrl = BUSINESS_REPORT)),
        )
        assertEquals(false, vm.uiState.value.evaluationCompleted)
        vm.consumeEvaluationForm("mock-record")
        vm.onEvaluationH5Closed()
        assertEquals(true, vm.uiState.value.evaluationCompleted)
        assertNull(vm.uiState.value.evaluationResult)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("A级", vm.uiState.value.evaluationResult?.pgResult)
        vm.resetEvaluationResult()
        assertEquals(false, vm.uiState.value.evaluationCompleted)
        assertNull(vm.uiState.value.evaluationFormRequest)
    }

    @Test fun `missing form URL does not fall back to vendor URL`() = runTest {
        val viewModel = completeEvaluation(
            ApiResult.Success(UserLatentDetailModel(id = 7, userName = "Mock 客户", pgUrl = null))
        )
        assertNotNull(viewModel.uiState.value.evaluationFormRequest)
        assertNull(viewModel.uiState.value.selectedCustomer?.pgUrl)
        assertNotNull(viewModel.uiState.value.evaluationFormRequest?.errorMessage)
        assertEquals(viewModel.uiState.value.selectedCustomer?.pgUrl, viewModel.uiState.value.evaluationFormRequest?.url)
    }

    @Test fun `mock detail failure after upload remains retryable without repeating upload`() = runTest {
        val viewModel = completeEvaluation(ApiResult.Failure(503, "Mock 客户详情暂不可用"))
        assertNotNull(viewModel.uiState.value.evaluationFormRequest)
        assertNull(viewModel.uiState.value.customerDetailErrorMessage)
        assertEquals(BUSINESS_REPORT, viewModel.uiState.value.selectedCustomer?.pgUrl)
        assertEquals(viewModel.uiState.value.selectedCustomer?.pgUrl, viewModel.uiState.value.evaluationFormRequest?.url)
    }

    @Test fun `consumed request is not rearmed by duplicate completion`() = runTest {
        val vm = completeEvaluation(ApiResult.Success(UserLatentDetailModel(id = 7, pgUrl = BUSINESS_REPORT)))
        vm.consumeEvaluationForm("mock-record")
        vm.onSdkEvent(com.ytone.longcare.integration.qlz.QlzSdkEvent.Completed("duplicate", VENDOR_REPORT, "80"))
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.evaluationFormRequest?.consumed)
        assertEquals("mock-record", vm.uiState.value.evaluationFormRequest?.recordId)
    }

    @Test fun `missing URL can be fetched again without another SDK session`() = runTest {
        val vm = completeEvaluation(ApiResult.Success(UserLatentDetailModel(id = 7, pgUrl = null)))
        vm.retryEvaluationForm()
        advanceUntilIdle()
        assertEquals(BUSINESS_REPORT, vm.uiState.value.evaluationFormRequest?.url)
        assertNull(vm.uiState.value.evaluationFormRequest?.errorMessage)
    }

    @Test fun `switching customer clears pending navigation`() = runTest {
        val vm = completeEvaluation(ApiResult.Success(UserLatentDetailModel(id = 7, pgUrl = BUSINESS_REPORT)))
        vm.loadCustomerDetail(8)
        advanceUntilIdle()
        assertNull(vm.uiState.value.evaluationFormRequest)
    }

    private suspend fun TestScope.completeEvaluation(
        refreshedDetail: ApiResult<UserLatentDetailModel>,
    ): SalesViewModel {
        val initialDetail = UserLatentDetailModel(id = 7, userName = "Mock 客户", pgUrl = null)
        val repository = mockk<SaleRepository> {
            coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
            coEvery { getUserLatentDetail(7) } returnsMany listOf(
                ApiResult.Success(initialDetail), refreshedDetail,
                ApiResult.Success(initialDetail.copy(pgUrl = BUSINESS_REPORT)),
            )
            coEvery { getCheckToken(7, "mock-device") } returns
                ApiResult.Success(CheckTokenModel(token = "mock-token"))
            coEvery { getCheckResult(7, "mock-record") } returns
                ApiResult.Success(CheckResultModel("A级", BUSINESS_REPORT))
        }
        val context = mockk<Context>(relaxed = true)
        val viewModel = SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = UnusedPhotoCloudUploader,
            imagePipeline = testImagePipeline(context),
            evaluationDeviceGateway = mockk<SalesEvaluationDeviceGateway> {
                every { getDeviceId() } returns Result.success("mock-device")
                every { getConnectedDeviceName() } returns "Mock 检测设备"
            },
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            textResolver = ResourceTextResolver(context),
        )
        viewModel.loadCustomerDetail(7)
        advanceUntilIdle()
        assertEquals(initialDetail, viewModel.uiState.value.selectedCustomer)
        viewModel.prepareEvaluation(7)
        advanceUntilIdle()

        lateinit var callback: (QlzEvaluationDriverEvent) -> Unit
        val driver = mockk<QlzEvaluationDriver>(relaxed = true) {
            every { authorize("mock-token", any()) } answers { callback = secondArg() }
        }
        val session = QlzEvaluationSession(
            driverFactory = QlzEvaluationDriverFactory { driver },
            uploadContext = viewModel.uiState.value.toQlzEvaluationUploadContext(""),
            onEvent = viewModel::onSdkEvent,
            releaseLease = {},
        )
        try {
            session.start(requireNotNull(viewModel.uiState.value.checkToken).token)
            callback(QlzEvaluationDriverEvent.Authorized)
            callback(QlzEvaluationDriverEvent.DevicesChanged(
                listOf(QlzDeviceOption("mock-device", "Mock 检测设备", "••••"))
            ))
            session.selectDevice("mock-device")
            callback(QlzEvaluationDriverEvent.CheckStarted)
            callback(QlzEvaluationDriverEvent.ProgressChanged(5, 5))
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.evaluationFormRequest)
            callback(QlzEvaluationDriverEvent.MeasurementCompleted)
            callback(QlzEvaluationDriverEvent.MeasurementCompleted)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.evaluationFormRequest)
            callback(QlzEvaluationDriverEvent.UploadSucceeded("mock-record", VENDOR_REPORT, "80"))
            callback(QlzEvaluationDriverEvent.UploadSucceeded("duplicate", VENDOR_REPORT, "80"))
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.getCheckToken(7, "mock-device") }
            coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
            verify(exactly = 1) { driver.upload(QlzEvaluationUploadContext()) }
            if (refreshedDetail is ApiResult.Failure) {
                assertEquals(refreshedDetail.message, viewModel.uiState.value.customerDetailErrorMessage)
                viewModel.retryCustomerDetail()
                advanceUntilIdle()
                assertNull(viewModel.uiState.value.customerDetailErrorMessage)
                assertEquals(BUSINESS_REPORT, viewModel.uiState.value.selectedCustomer?.pgUrl)
                coVerify(exactly = 3) { repository.getUserLatentDetail(7) }
                verify(exactly = 1) { driver.upload(any()) }
            }
        } finally {
            session.close()
        }
        return viewModel
    }

    private companion object {
        const val BUSINESS_REPORT = "https://business.invalid/mock-report"
        const val VENDOR_REPORT = "https://vendor.invalid/mock-report"
    }
}
