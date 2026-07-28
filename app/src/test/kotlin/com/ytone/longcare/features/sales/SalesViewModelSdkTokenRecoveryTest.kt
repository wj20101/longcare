package com.ytone.longcare.features.sales

import android.app.Activity
import android.content.Context
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelSdkTokenRecoveryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `expired SDK token is refreshed once and relaunched without a retry loop`() =
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
            val callback = slot<(QlzSdkEvent) -> Unit>()
            val qlzSdkClient =
                mockk<QlzSdkClient>(relaxed = true) {
                    every { getDeviceId() } returns Result.success("device-1")
                    every { getConnectedDeviceName() } returns "QLZ-device"
                    every {
                        openByToken(any(), any(), capture(callback))
                    } returns Unit
                }
            val activity =
                mockk<Activity>(relaxed = true) {
                    every { isFinishing } returns false
                    every { isDestroyed } returns false
                }
            val viewModel = createViewModel(repository, qlzSdkClient)

            viewModel.prepareEvaluation(7)
            viewModel.launchSdk(activity)
            callback.captured(
                QlzSdkEvent.Error(
                    code = 100,
                    message = "token expired",
                )
            )

            assertEquals(2, tokenRequests)
            verify(exactly = 1) {
                qlzSdkClient.openByToken(activity, "old-token", any())
            }
            verify(exactly = 1) {
                qlzSdkClient.openByToken(activity, "new-token", any())
            }
            assertEquals("new-token", viewModel.uiState.value.checkToken?.token)

            callback.captured(
                QlzSdkEvent.Error(
                    code = 100,
                    message = "token expired again",
                )
            )

            assertEquals(2, tokenRequests)
            verify(exactly = 2) {
                qlzSdkClient.openByToken(activity, any(), any())
            }
            assertTrue(
                viewModel.uiState.value.errorMessage
                    .orEmpty()
                    .contains("重新进入评估页面")
            )
        }

    private fun createViewModel(
        repository: SaleRepository,
        qlzSdkClient: QlzSdkClient,
    ): SalesViewModel =
        SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            cosRepository = mockk<CosRepository>(relaxed = true),
            qlzSdkClient = qlzSdkClient,
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            applicationContext = mockk<Context>(relaxed = true),
        )
}
