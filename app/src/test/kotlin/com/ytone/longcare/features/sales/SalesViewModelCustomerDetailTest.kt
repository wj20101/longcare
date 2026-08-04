package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelCustomerDetailTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `nullable customer detail loads without global blocking`() =
        runTest {
            val detail =
                UserLatentDetailModel(
                    id = 7,
                    userName = "测试客户",
                    guardianName = null,
                    checkTime = null,
                    pgResult = null,
                    pgUrl = null,
                )
            val repository = repositoryWithDetail(ApiResult.Success(detail))
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertEquals(detail, viewModel.uiState.value.selectedCustomer)
            assertEquals(7, viewModel.uiState.value.selectedCustomerId)
            assertNull(viewModel.uiState.value.customerDetailErrorMessage)
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `failed customer detail stays retryable and retry replaces the error`() =
        runTest {
            var attempts = 0
            val recoveredDetail =
                UserLatentDetailModel(
                    id = 7,
                    userName = "已恢复客户",
                )
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getUserLatentDetail(7) } answers {
                        if (attempts++ == 0) {
                            ApiResult.Failure(
                                code = 500,
                                message = "客户服务繁忙",
                            )
                        } else {
                            ApiResult.Success(recoveredDetail)
                        }
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertNull(viewModel.uiState.value.selectedCustomer)
            assertEquals(
                "客户服务繁忙",
                viewModel.uiState.value.customerDetailErrorMessage,
            )
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.retryCustomerDetail()

            assertEquals(2, attempts)
            assertEquals(recoveredDetail, viewModel.uiState.value.selectedCustomer)
            assertNull(viewModel.uiState.value.customerDetailErrorMessage)
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
        }

    @Test
    fun `mismatched customer id is rejected instead of showing another customer`() =
        runTest {
            val repository =
                repositoryWithDetail(
                    ApiResult.Success(
                        UserLatentDetailModel(
                            id = 8,
                            userName = "其他客户",
                        )
                    )
                )
            val viewModel = createViewModel(repository)

            viewModel.loadCustomerDetail(7)

            assertNull(viewModel.uiState.value.selectedCustomer)
            assertEquals(
                "客户详情数据异常，请重试",
                viewModel.uiState.value.customerDetailErrorMessage,
            )
            assertFalse(viewModel.uiState.value.isCustomerDetailLoading)
        }

    private fun repositoryWithDetail(
        result: ApiResult<UserLatentDetailModel>,
    ): SaleRepository =
        mockk<SaleRepository>(relaxed = true) {
            coEvery { getRecentUserLatentList() } returns
                ApiResult.Success(emptyList())
            coEvery { getUserLatentDetail(any()) } returns result
        }

    private fun createViewModel(repository: SaleRepository): SalesViewModel =
        SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            cosRepository = mockk<CosRepository>(relaxed = true),
            qlzSdkClient = mockk<QlzSdkClient>(relaxed = true),
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            applicationContext = mockk<Context>(relaxed = true),
        )
}
