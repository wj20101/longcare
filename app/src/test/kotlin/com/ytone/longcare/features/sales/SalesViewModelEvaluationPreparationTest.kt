package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelEvaluationPreparationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `device id is obtained before requesting the exact token inputs`() =
        runTest {
            val order = mutableListOf<String>()
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getCheckToken(42, "device-exact") } answers {
                        order += "request-token"
                        ApiResult.Success(CheckTokenModel(token = "one-time-token"))
                    }
                }
            val deviceGateway =
                mockk<SalesEvaluationDeviceGateway>(relaxed = true) {
                    every { getDeviceId() } answers {
                        order += "get-device-id"
                        Result.success("device-exact")
                    }
                    every { getConnectedDeviceName() } returns "QLZ device"
                }
            val viewModel = createViewModel(repository, deviceGateway)

            viewModel.prepareEvaluation(42)
            advanceUntilIdle()

            assertEquals(listOf("get-device-id", "request-token"), order)
            assertEquals("device-exact", viewModel.uiState.value.sdkDeviceId)
            assertEquals("one-time-token", viewModel.uiState.value.checkToken?.token)
            coVerify(exactly = 1) {
                repository.getCheckToken(
                    customerId = 42,
                    checkDeviceId = "device-exact",
                )
            }
        }

    private fun createViewModel(
        repository: SaleRepository,
        deviceGateway: SalesEvaluationDeviceGateway,
    ): SalesViewModel {
        val context = mockk<Context>(relaxed = true)
        return SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = UnusedPhotoCloudUploader,
            imagePipeline = mockk<UnifiedImagePipeline>(relaxed = true),
            evaluationDeviceGateway = deviceGateway,
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            textResolver = ResourceTextResolver(context),
        )
    }
}
