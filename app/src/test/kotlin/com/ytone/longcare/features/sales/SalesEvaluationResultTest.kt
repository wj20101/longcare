package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.CheckResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.platform.sales.SalesEvaluationFormRequest
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

    @Test fun `H5 close completes immediately and result is fetched only by completion page`() = runTest {
        coEvery { repository.getCheckResult(7, "record") } returns ApiResult.Success(CheckResultModel("A级", "report"))
        val vm = createViewModel(withDevice = true)
        advanceUntilIdle()
        vm.onEvaluationH5Closed()
        assertTrue(vm.uiState.value.evaluationCompleted)
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("A级", vm.uiState.value.evaluationResult?.pgResult)
        assertEquals("report", vm.uiState.value.evaluationResult?.pgUrl)
        coVerify(exactly = 1) { repository.getCheckResult(7, "record") }
        coVerify(exactly = 0) { repository.getUserLatentDetail(any()) }
    }

    @Test fun `manual form uses null record and displays backend text without mapping`() = runTest {
        coEvery { repository.getCheckResult(7, null) } returns ApiResult.Success(CheckResultModel("重度失能"))
        val vm = createViewModel()
        vm.onEvaluationH5Closed()
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertEquals("重度失能", vm.uiState.value.evaluationResult?.pgResult)
    }

    @Test fun `failure and empty result keep completed state and refresh only queries result`() = runTest {
        coEvery { repository.getCheckResult(7, null) } returnsMany listOf(
            ApiResult.Failure(500, "失败"),
            ApiResult.Success(CheckResultModel()),
            ApiResult.Success(CheckResultModel("B级")),
        )
        val vm = createViewModel()
        vm.onEvaluationH5Closed()
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
    }

    @Test fun `ordinary back does not complete or query and completion can restore`() = runTest {
        val handle = SavedStateHandle(mapOf("sales.evaluation.customer" to 7))
        val vm = createViewModel(handle = handle)
        vm.loadEvaluationResult()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.evaluationCompleted)
        coVerify(exactly = 0) { repository.getCheckResult(any(), any()) }
        vm.onEvaluationH5Closed()
        val restored = createViewModel(handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertTrue(restored.uiState.value.evaluationCompleted)
        assertEquals(7, restored.uiState.value.selectedCustomerId)
        restored.resetEvaluationResult()
        assertFalse(restored.uiState.value.evaluationCompleted)
    }

    @Test fun `late result cannot overwrite a different customer`() = runTest {
        val response = CompletableDeferred<ApiResult<CheckResultModel>>()
        coEvery { repository.getCheckResult(7, null) } coAnswers {
            withContext(NonCancellable) { response.await() }
        }
        val vm = createViewModel()
        vm.onEvaluationH5Closed()
        vm.loadEvaluationResult()
        runCurrent()
        vm.loadCustomerDetail(8)
        response.complete(ApiResult.Success(CheckResultModel("旧客户A级")))
        advanceUntilIdle()
        assertEquals(8, vm.uiState.value.selectedCustomerId)
        assertNull(vm.uiState.value.evaluationResult)
        assertFalse(vm.uiState.value.evaluationCompleted)
    }

    private fun createViewModel(
        withDevice: Boolean = false,
        handle: SavedStateHandle = SavedStateHandle(mapOf(
            "sales.evaluation.customer" to 7,
            "sales.evaluation.form" to if (withDevice) SalesEvaluationFormRequest(
                7, "record", "https://internal.test/form", consumed = true,
            ) else null,
        )),
    ): SalesViewModel {
        val context = mockk<Context>(relaxed = true)
        return SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
            testImagePipeline(context), mockk(relaxed = true), mockk(relaxed = true),
            ResourceTextResolver(context), handle)
    }
}
