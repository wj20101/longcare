package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.CheckResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesEvaluationResultTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = mockk<SaleRepository> {
        coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
        coEvery { getUserLatentDetail(any()) } answers {
            ApiResult.Success(UserLatentDetailModel(id = firstArg(), pgResult = "旧结果"))
        }
    }

    @Test fun `SDK upload completes immediately and result is fetched only by result page`() = runTest {
        coEvery { repository.getCheckResult(7, "record") } returns ApiResult.Success(CheckResultModel("A级", "report"))
        val vm = createViewModel()
        vm.prepareEvaluation(7)
        vm.onSdkEvent(QlzSdkEvent.Completed("record"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.evaluationCompleted)
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("A级", vm.uiState.value.evaluationResult?.pgResult)
        assertEquals("report", vm.uiState.value.evaluationResult?.pgUrl)
        coVerify(exactly = 1) { repository.getCheckResult(7, "record") }
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    @Test fun `manual form fetches fresh detail rather than cached grade or device result`() = runTest {
        val vm = createViewModel()
        vm.loadCustomerDetail(7)
        advanceUntilIdle()
        coEvery { repository.getUserLatentDetail(7) } returns ApiResult.Success(
            UserLatentDetailModel(id = 7, pgResult = "重度失能", pgUrl = "fresh-report"),
        )
        vm.onEvaluationH5Closed()
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("重度失能", vm.uiState.value.evaluationResult?.pgResult)
        assertEquals("fresh-report", vm.uiState.value.evaluationResult?.pgUrl)
        coVerify(exactly = 2) { repository.getUserLatentDetail(7) }
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
    }

    @Test fun `failure and empty result keep completed state and refresh only queries result`() = runTest {
        coEvery { repository.getCheckResult(7, "record") } returnsMany listOf(
            ApiResult.Failure(500, "失败"),
            ApiResult.Success(CheckResultModel()),
            ApiResult.Success(CheckResultModel("B级")),
        )
        val vm = createViewModel(withDevice = true)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.evaluationCompleted)
        assertTrue(vm.uiState.value.evaluationResultError)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertNull(vm.uiState.value.evaluationResult?.pgResult)
        assertFalse(vm.uiState.value.evaluationResultError)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("B级", vm.uiState.value.evaluationResult?.pgResult)
        coVerify(exactly = 0) { repository.getCheckToken(any(), any()) }
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
        coVerify(exactly = 3) { repository.getCheckResult(7, "record") }
    }

    @Test fun `manual form failure exception and empty result refresh the same detail endpoint`() = runTest {
        coEvery { repository.getUserLatentDetail(7) } returnsMany listOf(
            ApiResult.Failure(500, "失败"),
            ApiResult.Exception(IllegalStateException("offline")),
            ApiResult.Success(UserLatentDetailModel(id = 7)),
            ApiResult.Success(UserLatentDetailModel(id = 7, pgResult = "B级", pgUrl = "report")),
        )
        val vm = createViewModel()
        vm.onEvaluationH5Closed()
        repeat(2) {
            vm.loadEvaluationResult()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.evaluationCompleted)
            assertTrue(vm.uiState.value.evaluationResultError)
        }
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.evaluationResultError)
        assertNull(vm.uiState.value.evaluationResult?.pgResult)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("B级", vm.uiState.value.evaluationResult?.pgResult)
        assertEquals("report", vm.uiState.value.evaluationResult?.pgUrl)
        coVerify(exactly = 4) { repository.getUserLatentDetail(7) }
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
        coVerify(exactly = 0) { repository.getCheckToken(any(), any()) }
    }

    @Test fun `ordinary back does not complete or query and completion can restore`() = runTest {
        val handle = SavedStateHandle(mapOf("sales.evaluation.customer" to 7))
        val vm = createViewModel(handle = handle)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.evaluationCompleted)
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
        vm.onEvaluationH5Closed()
        val restored = createViewModel(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertTrue(restored.uiState.value.evaluationCompleted)
        assertEquals(7, restored.uiState.value.selectedCustomerId)
        restored.resetEvaluationResult()
        assertFalse(restored.uiState.value.evaluationCompleted)
    }

    @Test fun `late result cannot overwrite a different customer`() = runTest {
        val response = CompletableDeferred<ApiResult<UserLatentDetailModel>>()
        coEvery { repository.getUserLatentDetail(7) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val vm = createViewModel()
        vm.onEvaluationH5Closed()
        vm.loadEvaluationResult()
        runCurrent()
        vm.loadCustomerDetail(8)
        response.complete(ApiResult.Success(UserLatentDetailModel(id = 7, pgResult = "旧客户A级")))
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.selectedCustomerId)
        assertNull(vm.uiState.value.evaluationResult)
        assertFalse(vm.uiState.value.evaluationCompleted)
    }

    @Test fun `device record and completion restore without H5 request or customer refresh`() = runTest {
        val handle = SavedStateHandle(mapOf("sales.evaluation.customer" to 7))
        val vm = createViewModel(handle = handle)
        vm.prepareEvaluation(7)
        vm.onSdkEvent(QlzSdkEvent.Completed("record"))
        advanceUntilIdle()
        val restored = createViewModel(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertTrue(restored.uiState.value.evaluationCompleted)
        assertEquals("record", restored.uiState.value.evaluationRecordId)
        coEvery { repository.getCheckResult(7, "record") } returns ApiResult.Success(CheckResultModel("A级", "report"))
        restored.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("report", restored.uiState.value.evaluationResult?.pgUrl)
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    @Test fun `late device result is discarded on customer switch`() = runTest {
        val response = CompletableDeferred<ApiResult<CheckResultModel>>()
        coEvery { repository.getCheckResult(7, "record") } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val vm = createViewModel(withDevice = true)
        vm.loadEvaluationResult()
        runCurrent()
        vm.loadCustomerDetail(8)
        response.complete(ApiResult.Success(CheckResultModel("旧等级", "old-report")))
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.selectedCustomerId)
        assertNull(vm.uiState.value.evaluationRecordId)
        assertNull(vm.uiState.value.evaluationResult)
        assertFalse(vm.uiState.value.evaluationCompleted)
    }

    @Test fun `blank device record never falls back to manual form detail`() = runTest {
        coEvery { repository.getCheckResult(7, "") } returns ApiResult.Failure(2001, "参数错误")
        val vm = createViewModel()
        vm.prepareEvaluation(7)
        vm.onSdkEvent(QlzSdkEvent.Completed(""))
        advanceUntilIdle()
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.evaluationResultError)
        coVerify(exactly = 1) { repository.getCheckResult(7, "") }
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    private fun createViewModel(
        withDevice: Boolean = false,
        handle: SavedStateHandle = SavedStateHandle(mapOf(
            "sales.evaluation.customer" to 7,
            "sales.evaluation.record" to if (withDevice) "record" else null,
            "sales.evaluation.completed" to withDevice,
        )),
    ): SalesViewModel {
        val context = mockk<Context>(relaxed = true)
        return SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
            testImagePipeline(context), mockk(relaxed = true), mockk(relaxed = true),
            ResourceTextResolver(context), handle)
    }
}
