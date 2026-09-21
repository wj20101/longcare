package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.R
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelSdkTokenRecoveryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `expired SDK token emits one UI relaunch request without a retry loop`() =
        runTest {
            var tokenRequests = 0
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getCheckToken(7, "device-1") } answers {
                        tokenRequests += 1
                        ApiResult.Success(
                            CheckTokenModel(
                                token =
                                    if (tokenRequests == 1) {
                                        "old-token"
                                    } else {
                                        "new-token"
                                    }
                            )
                        )
                    }
                }
            val evaluationDeviceGateway =
                mockk<SalesEvaluationDeviceGateway>(relaxed = true) {
                    every { getDeviceId() } returns Result.success("device-1")
                }
            val viewModel = createViewModel(repository, evaluationDeviceGateway)

            viewModel.prepareEvaluation(7)
            viewModel.requestSdkAuthorization()
            advanceUntilIdle()
            viewModel.consumeSdkLaunchRequest(requireNotNull(viewModel.uiState.value.sdkLaunchRequest))
            viewModel.onSdkEvent(
                QlzSdkEvent.Error(
                    code = 100,
                    message = "token expired",
                )
            )
            advanceUntilIdle()

            assertEquals(2, tokenRequests)
            assertEquals("new-token", viewModel.uiState.value.sdkLaunchRequest?.token)
            viewModel.consumeSdkLaunchRequest(requireNotNull(viewModel.uiState.value.sdkLaunchRequest))

            viewModel.onSdkEvent(
                QlzSdkEvent.Error(
                    code = 100,
                    message = "token expired again",
                )
            )
            advanceUntilIdle()

            assertEquals(2, tokenRequests)
            assertTrue(
                viewModel.uiState.value.errorMessage
                    .orEmpty()
                    .contains("重新进入评估页面")
            )
        }

    @Test
    fun `GetCheckToken business failure uses exit dialog instead of snackbar`() =
        runTest {
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getCheckToken(7, "device-1") } returns
                        ApiResult.Failure(code = 4001, message = "")
                }
            val evaluationDeviceGateway =
                mockk<SalesEvaluationDeviceGateway>(relaxed = true) {
                    every { getDeviceId() } returns Result.success("device-1")
                }
            val viewModel = createViewModel(repository, evaluationDeviceGateway)

            viewModel.prepareEvaluation(7)
            viewModel.requestSdkAuthorization()
            advanceUntilIdle()

            assertEquals(
                "评估准备失败，请稍后重试",
                viewModel.uiState.value.evaluationPrepareErrorMessage,
            )
            assertNull(viewModel.uiState.value.errorMessage)

            viewModel.clearEvaluationPrepareError()

            assertNull(viewModel.uiState.value.evaluationPrepareErrorMessage)
        }

    @Test
    fun `GetCheckToken success without a token uses exit dialog`() =
        runTest {
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getCheckToken(7, "device-1") } returns
                        ApiResult.Success(CheckTokenModel(token = ""))
                }
            val evaluationDeviceGateway =
                mockk<SalesEvaluationDeviceGateway>(relaxed = true) {
                    every { getDeviceId() } returns Result.success("device-1")
                }
            val viewModel = createViewModel(repository, evaluationDeviceGateway)

            viewModel.prepareEvaluation(7)
            viewModel.requestSdkAuthorization()
            advanceUntilIdle()

            assertEquals(
                "评估准备失败，请重新进入评估页面",
                viewModel.uiState.value.evaluationPrepareErrorMessage,
            )
            assertNull(viewModel.uiState.value.sdkLaunchRequest)
        }

    @Test
    fun `preparing does not fetch and each explicit authorization delivers a fresh one-use request`() = runTest {
        var requests = 0
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(any(), any()) } answers {
            ApiResult.Success(CheckTokenModel(token = "token-${++requests}"))
        }
        val vm = createViewModel(repository, gateway())
        vm.prepareEvaluation(7)
        advanceUntilIdle()
        assertEquals(0, requests)
        repeat(3) { index ->
            vm.requestSdkAuthorization()
            vm.requestSdkAuthorization()
            advanceUntilIdle()
            val request = requireNotNull(vm.uiState.value.sdkLaunchRequest)
            assertEquals("token-${index + 1}", request.token)
            vm.requestSdkAuthorization()
            advanceUntilIdle()
            assertEquals(index + 1, requests)
            assertTrue(vm.consumeSdkLaunchRequest(request))
            assertFalse(vm.consumeSdkLaunchRequest(request))
            assertNull(vm.uiState.value.sdkLaunchRequest)
        }
    }

    @Test
    fun `cancelled old response and finally cannot overwrite a new customer request`() = runTest {
        val old = CompletableDeferred<ApiResult<CheckTokenModel>>()
        val fresh = CompletableDeferred<ApiResult<CheckTokenModel>>()
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(7, any()) } coAnswers { withContext(NonCancellable) { old.await() } }
        coEvery { repository.getCheckToken(8, any()) } coAnswers { fresh.await() }
        val vm = createViewModel(repository, gateway())
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        runCurrent()
        vm.prepareEvaluation(8)
        vm.requestSdkAuthorization()
        runCurrent()
        old.complete(ApiResult.Success(CheckTokenModel(token = "stale")))
        runCurrent()
        assertTrue(vm.uiState.value.isSdkTokenLoading)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.sdkLaunchRequest)
        fresh.complete(ApiResult.Success(CheckTokenModel(token = "fresh")))
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.selectedCustomerId)
        assertEquals("fresh", vm.uiState.value.sdkLaunchRequest?.token)
        assertFalse(vm.uiState.value.isSdkTokenLoading)
    }

    @Test
    fun `exit discards late response and queued SDK callbacks`() = runTest {
        val response = CompletableDeferred<ApiResult<CheckTokenModel>>()
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val vm = createViewModel(repository, gateway())
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        runCurrent()
        vm.cancelSdkAuthorization()
        vm.onSdkEvent(QlzSdkEvent.Completed("late"))
        response.complete(ApiResult.Success(CheckTokenModel(token = "stale")))
        advanceUntilIdle()
        assertNull(vm.uiState.value.sdkLaunchRequest)
        assertFalse(vm.uiState.value.evaluationCompleted)
        assertFalse(vm.uiState.value.isSdkTokenLoading)
    }

    @Test
    fun `failed upload token refresh stops with no old credential or automatic loop`() = runTest {
        var requests = 0
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(any(), any()) } answers {
            requests++
            if (requests == 1) ApiResult.Success(CheckTokenModel(token = "first"))
            else ApiResult.Exception(IllegalStateException("offline"))
        }
        val vm = createViewModel(repository, gateway())
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        advanceUntilIdle()
        vm.consumeSdkLaunchRequest(requireNotNull(vm.uiState.value.sdkLaunchRequest))
        vm.onSdkEvent(QlzSdkEvent.Error(100, ""))
        advanceUntilIdle()
        assertNull(vm.uiState.value.sdkLaunchRequest)
        assertFalse(vm.uiState.value.evaluationCompleted)
        assertEquals("评估准备失败，请稍后重试", vm.uiState.value.evaluationPrepareErrorMessage)
        vm.onSdkEvent(QlzSdkEvent.Error(100, ""))
        advanceUntilIdle()
        assertEquals(2, requests)
    }

    @Test
    fun `old launch request cannot consume a new request with the same token text`() = runTest {
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(any(), any()) } returns ApiResult.Success(CheckTokenModel(token = "same-text"))
        val vm = createViewModel(repository, gateway())
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        advanceUntilIdle()
        val old = requireNotNull(vm.uiState.value.sdkLaunchRequest)
        assertTrue(vm.consumeSdkLaunchRequest(old))
        vm.requestSdkAuthorization()
        advanceUntilIdle()
        val fresh = requireNotNull(vm.uiState.value.sdkLaunchRequest)
        assertFalse(vm.consumeSdkLaunchRequest(old))
        assertTrue(vm.consumeSdkLaunchRequest(fresh))
    }

    @Test
    fun `cancel only clears SDK loading without overwriting another operation`() = runTest {
        val customers = CompletableDeferred<ApiResult<List<com.ytone.longcare.model.UserLatentListModel>>>()
        val token = CompletableDeferred<ApiResult<CheckTokenModel>>()
        val repository = tokenRepository()
        coEvery { repository.getRecentUserLatentList() } coAnswers { customers.await() }
        coEvery { repository.getCheckToken(any(), any()) } coAnswers { token.await() }
        val vm = createViewModel(repository, gateway())
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        val operation = vm.uiState.value.operation
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        runCurrent()
        assertTrue(vm.uiState.value.isSdkTokenLoading)
        vm.cancelSdkAuthorization()
        assertFalse(vm.uiState.value.isSdkTokenLoading)
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(operation, vm.uiState.value.operation)
        customers.complete(ApiResult.Success(emptyList()))
        advanceUntilIdle()
    }

    @Test
    fun `queued callback from a cancelled flow cannot complete the next customer`() = runTest {
        val vm = createViewModel(tokenRepository(), gateway())
        vm.prepareEvaluation(7)
        vm.onSdkEvent(QlzSdkEvent.Completed("stale"))
        vm.prepareEvaluation(8)
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.selectedCustomerId)
        assertFalse(vm.uiState.value.evaluationCompleted)
        assertNull(vm.uiState.value.evaluationRecordId)
    }

    @Test
    fun `clearing ViewModel also cancels the SDK flow and drops late token responses`() = runTest {
        val response = CompletableDeferred<ApiResult<CheckTokenModel>>()
        val repository = tokenRepository()
        coEvery { repository.getCheckToken(any(), any()) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val vm = createViewModel(repository, gateway())
        val store = androidx.lifecycle.ViewModelStore().apply { put("sales", vm) }
        vm.prepareEvaluation(7)
        vm.requestSdkAuthorization()
        runCurrent()
        store.clear()
        response.complete(ApiResult.Success(CheckTokenModel(token = "late")))
        advanceUntilIdle()
        assertNull(vm.uiState.value.sdkLaunchRequest)
    }

    private fun tokenRepository() = mockk<SaleRepository>(relaxed = true) {
        coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
    }

    private fun gateway() = mockk<SalesEvaluationDeviceGateway>(relaxed = true) {
        every { getDeviceId() } returns Result.success("device-1")
    }

    private fun createViewModel(
        repository: SaleRepository,
        evaluationDeviceGateway: SalesEvaluationDeviceGateway,
    ): SalesViewModel {
        val applicationContext =
            mockk<Context>(relaxed = true) {
                every { getString(R.string.sales_error_evaluation_expired) } returns
                    "本次评估已失效，请重新进入评估页面"
                every { getString(R.string.sales_error_evaluation_prepare) } returns
                    "评估准备失败，请稍后重试"
                every { getString(R.string.sales_error_evaluation_credential) } returns
                    "评估准备失败，请重新进入评估页面"
            }
        return SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = UnusedPhotoCloudUploader,
            imagePipeline = testImagePipeline(applicationContext),
            evaluationDeviceGateway = evaluationDeviceGateway,
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            textResolver = ResourceTextResolver(applicationContext),
        )
    }
}
