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
        for ((authFailure, refreshFails) in listOf(null to false, 401 to false, 2001 to false, 401 to true)) {
            val repository = mockk<SaleRepository> {
                coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                coEvery { getUserLatentDetail(7) } returns ApiResult.Success(
                    UserLatentDetailModel(id = 7, pgUrl = "https://business.invalid/old-form"),
                )
                coEvery { getCheckToken(7, "device") } returnsMany listOf(
                    ApiResult.Success(CheckTokenModel(token = "token")),
                    if (refreshFails) ApiResult.Failure(503, "暂不可用") else ApiResult.Success(CheckTokenModel(token = "fresh")),
                )
                coEvery { getCheckResult(7, "record") } returnsMany listOf(
                    ApiResult.Failure(503, "结果暂不可用"),
                    ApiResult.Success(CheckResultModel("A级", "https://business.invalid/new-report")),
                )
            }
            val context = mockk<Context>(relaxed = true)
            val vm = SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
                testImagePipeline(context), mockk<SalesEvaluationDeviceGateway> {
                    every { getDeviceId() } returns Result.success("device")
                }, mockk(relaxed = true), ResourceTextResolver(context), SavedStateHandle())
            vm.loadCustomerDetail(7)
            advanceUntilIdle()
            vm.prepareEvaluation(7)
            vm.requestSdkAuthorization()
            advanceUntilIdle()

            lateinit var emit: (QlzEvaluationDriverEvent) -> Unit
            val driver = mockk<QlzEvaluationDriver>(relaxed = true) {
                every { authorize(any(), any()) } answers { emit = secondArg() }
            }
            val session = QlzEvaluationSession(driverFactory = QlzEvaluationDriverFactory { driver },
                uploadContext = QlzEvaluationUploadContext(), onEvent = vm::onSdkEvent, releaseLease = {})
            try {
                val request = requireNotNull(vm.uiState.value.sdkLaunchRequest)
                vm.consumeSdkLaunchRequest(request)
                session.authorize(request.token)
                emit(QlzEvaluationDriverEvent.Authorized)
                emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(QlzDeviceOption("device", "Mock", "••••"))))
                session.selectDevice("device")
                emit(QlzEvaluationDriverEvent.CheckStarted)
                emit(QlzEvaluationDriverEvent.ProgressChanged(5, 5))
                emit(QlzEvaluationDriverEvent.MeasurementCompleted)
                emit(QlzEvaluationDriverEvent.MeasurementCompleted)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.evaluationCompleted)
                if (authFailure != null) {
                    emit(QlzEvaluationDriverEvent.UploadFailed(authFailure))
                    advanceUntilIdle()
                    assertFalse(vm.uiState.value.evaluationCompleted)
                    if (refreshFails) {
                        assertNull(vm.uiState.value.sdkLaunchRequest)
                        assertEquals(QlzEvaluationRecoveryAction.EXIT, session.state.value.recoveryAction)
                        assertNotNull(vm.uiState.value.evaluationPrepareErrorMessage)
                        emit(QlzEvaluationDriverEvent.TokenExpired())
                        advanceUntilIdle()
                        coVerify(exactly = 2) { repository.getCheckToken(7, "device") }
                        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
                        verify(exactly = 0) { driver.retryUpload() }
                        verify(exactly = 0) { driver.close() }
                        continue
                    }
                    val recovery = requireNotNull(vm.uiState.value.sdkLaunchRequest)
                    assertEquals("fresh", recovery.token)
                    vm.consumeSdkLaunchRequest(recovery)
                    session.authorize(recovery.token)
                    emit(QlzEvaluationDriverEvent.Authorized)
                    verify(exactly = 1) { driver.retryUpload() }
                    verify(exactly = 0) { driver.close() }
                    coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
                }
                emit(QlzEvaluationDriverEvent.UploadSucceeded("record"))
                emit(QlzEvaluationDriverEvent.UploadSucceeded("duplicate"))
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
                vm.onSdkEvent(QlzSdkEvent.Completed("duplicate"))
                advanceUntilIdle()
                assertEquals("record", vm.uiState.value.evaluationRecordId)
                coVerify(exactly = 1) { repository.getUserLatentDetail(7) }
                coVerify(exactly = if (authFailure == null) 1 else 2) { repository.getCheckToken(7, "device") }
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
}
