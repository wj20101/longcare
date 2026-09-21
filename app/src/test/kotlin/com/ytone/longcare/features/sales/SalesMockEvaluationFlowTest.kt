package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.*
import com.ytone.longcare.model.CheckResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real session and ViewModel; vendor I/O and repository responses remain in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class SalesMockEvaluationFlowTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `upload enters result flow without fetching customer H5 or repeating upload`() = runTest {
        val repository = mockk<SaleRepository> {
            coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
            coEvery { getUserLatentDetail(7) } returns ApiResult.Success(
                UserLatentDetailModel(id = 7, pgUrl = "https://business.invalid/old-form"),
            )
            coEvery { getCheckToken(7, "device") } returns ApiResult.Success(CheckTokenModel(token = "token"))
            coEvery { getCheckResult(7, "record") } returnsMany listOf(
                ApiResult.Failure(503, "结果暂不可用"),
                ApiResult.Success(CheckResultModel("A级", "https://business.invalid/new-report")),
            )
        }
        val context = mockk<Context>(relaxed = true)
        val vm = SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
            testImagePipeline(context), mockk<SalesEvaluationDeviceGateway> {
                every { getDeviceId() } returns Result.success("device")
                every { getConnectedDeviceName() } returns "Mock 设备"
            }, mockk(relaxed = true), ResourceTextResolver(context), SavedStateHandle())
        vm.loadCustomerDetail(7)
        advanceUntilIdle()
        vm.prepareEvaluation(7)
        advanceUntilIdle()

        lateinit var emit: (QlzEvaluationDriverEvent) -> Unit
        val driver = mockk<QlzEvaluationDriver>(relaxed = true) {
            every { authorize("token", any()) } answers { emit = secondArg() }
        }
        val session = QlzEvaluationSession(driverFactory = QlzEvaluationDriverFactory { driver },
            uploadContext = QlzEvaluationUploadContext(), onEvent = vm::onSdkEvent, releaseLease = {})
        try {
            session.start(requireNotNull(vm.uiState.value.checkToken).token)
            emit(QlzEvaluationDriverEvent.Authorized)
            emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(QlzDeviceOption("device", "Mock", "••••"))))
            session.selectDevice("device")
            emit(QlzEvaluationDriverEvent.CheckStarted)
            emit(QlzEvaluationDriverEvent.ProgressChanged(5, 5))
            emit(QlzEvaluationDriverEvent.MeasurementCompleted)
            emit(QlzEvaluationDriverEvent.MeasurementCompleted)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.evaluationCompleted)
            emit(QlzEvaluationDriverEvent.UploadSucceeded("record", "https://vendor.invalid/report", "80"))
            emit(QlzEvaluationDriverEvent.UploadSucceeded("duplicate", "", ""))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.evaluationCompleted)
            assertEquals("record", vm.uiState.value.evaluationRecordId)
            assertNull(vm.uiState.value.evaluationResult)
            vm.loadEvaluationResult()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.evaluationResultError)
            vm.loadEvaluationResult()
            advanceUntilIdle()
            assertEquals(CheckResultModel("A级", "https://business.invalid/new-report"), vm.uiState.value.evaluationResult)
            vm.onSdkEvent(QlzSdkEvent.Completed("duplicate", "", ""))
            advanceUntilIdle()
            assertEquals("record", vm.uiState.value.evaluationRecordId)
            coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
            coVerify(exactly = 1) { repository.getCheckToken(7, "device") }
            coVerify(exactly = 2) { repository.getCheckResult(7, "record") }
            verify(exactly = 1) { driver.upload(any()) }
            vm.resetEvaluationResult()
            assertFalse(vm.uiState.value.evaluationCompleted)
            assertNull(vm.uiState.value.evaluationRecordId)
        } finally {
            session.close()
        }
        verify(exactly = 1) { driver.close() }
    }
}
