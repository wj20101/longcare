package com.ytone.longcare.features.sales

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.platform.sales.SalesEvaluationFormRequest
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import java.io.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesEvaluationFormRestoreTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `ready request survives state restoration and consumption stays saved`() = runTest {
        val handle = restoredHandle(SalesEvaluationFormRequest(7, "record", "https://internal.test/form"))
        val vm = createViewModel(handle)
        advanceUntilIdle()
        assertEquals("https://internal.test/form", vm.uiState.value.evaluationFormRequest?.url)
        assertEquals(false, vm.uiState.value.evaluationFormRequest?.consumed)
        vm.consumeEvaluationForm("record")
        val restored = createViewModel(restoredHandle(requireNotNull(handle[KEY])))
        advanceUntilIdle()
        assertEquals(true, restored.uiState.value.evaluationFormRequest?.consumed)
    }

    @Test fun `restored upload without URL retries only customer lookup`() = runTest {
        val vm = createViewModel(restoredHandle(SalesEvaluationFormRequest(7, "record")))
        advanceUntilIdle()
        assertEquals("https://internal.test/form", vm.uiState.value.evaluationFormRequest?.url)
        assertEquals(false, vm.uiState.value.evaluationFormRequest?.consumed)
    }

    private fun restoredHandle(request: SalesEvaluationFormRequest): SavedStateHandle {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(request) }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() }
        return SavedStateHandle(mapOf(KEY to restored))
    }

    private fun createViewModel(handle: SavedStateHandle): SalesViewModel {
        val context = mockk<Context>(relaxed = true)
        val repository = mockk<SaleRepository> {
            coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
            coEvery { getUserLatentDetail(7) } returns ApiResult.Success(
                UserLatentDetailModel(id = 7, pgUrl = "https://internal.test/form"),
            )
        }
        return SalesViewModel(repository, mockk(relaxed = true), UnusedPhotoCloudUploader,
            testImagePipeline(context), mockk(relaxed = true), mockk(relaxed = true),
            ResourceTextResolver(context), handle)
    }

    private companion object { const val KEY = "sales.evaluation.form" }
}
