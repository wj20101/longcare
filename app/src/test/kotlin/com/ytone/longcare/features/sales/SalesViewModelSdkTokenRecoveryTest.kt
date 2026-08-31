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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelSdkTokenRecoveryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
                    every { getConnectedDeviceName() } returns "QLZ-device"
                }
            val viewModel = createViewModel(repository, evaluationDeviceGateway)

            viewModel.prepareEvaluation(7)
            advanceUntilIdle()
            viewModel.onSdkEvent(
                QlzSdkEvent.Error(
                    code = 100,
                    message = "token expired",
                )
            )
            advanceUntilIdle()

            assertEquals(2, tokenRequests)
            assertEquals("new-token", viewModel.uiState.value.checkToken?.token)
            assertEquals("new-token", viewModel.uiState.value.sdkLaunchRequest?.token)

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
            advanceUntilIdle()

            assertEquals(
                "评估准备失败，请重新进入评估页面",
                viewModel.uiState.value.evaluationPrepareErrorMessage,
            )
            assertNull(viewModel.uiState.value.checkToken)
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
            textResolver = ResourceTextResolver(applicationContext),
        )
    }
}
