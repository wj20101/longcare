package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentListModel
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelCustomerListTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `recent customers stay on home and do not seed customer search results`() =
        runTest {
            val recentCustomers =
                listOf(
                    UserLatentListModel(
                        id = 1,
                        userName = "最近客户",
                    )
                )
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(recentCustomers)
                }

            val viewModel = createViewModel(repository)

            assertEquals(recentCustomers, viewModel.uiState.value.recentCustomers)
            assertTrue(viewModel.uiState.value.customers.isEmpty())
            assertEquals(
                UserLatentCheckState.ALL,
                viewModel.uiState.value.customerCheckState,
            )
        }

    @Test
    fun `non-empty username always searches all audit states`() =
        runTest {
            val requests = mutableListOf<SearchUserLatentParamModel>()
            val searchResults =
                listOf(
                    UserLatentListModel(
                        id = 2,
                        userName = "搜索客户",
                    )
                )
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { searchUserLatentList(any()) } answers {
                        requests += firstArg<SearchUserLatentParamModel>()
                        ApiResult.Success(searchResults)
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.searchCustomers(
                keyword = " 搜索客户 ",
                checkState = UserLatentCheckState.REJECTED,
            )

            assertEquals(
                listOf(
                    SearchUserLatentParamModel(
                        userName = "搜索客户",
                        checkState = UserLatentCheckState.ALL,
                    )
                ),
                requests,
            )
            assertEquals(
                UserLatentCheckState.ALL,
                viewModel.uiState.value.customerCheckState,
            )
            assertEquals(searchResults, viewModel.uiState.value.customers)
        }

    @Test
    fun `empty username preserves selected audit state`() =
        runTest {
            val requests = mutableListOf<SearchUserLatentParamModel>()
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { searchUserLatentList(any()) } answers {
                        requests += firstArg<SearchUserLatentParamModel>()
                        ApiResult.Success(emptyList())
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.searchCustomers(
                keyword = "   ",
                checkState = UserLatentCheckState.APPROVED,
            )

            assertEquals(
                listOf(
                    SearchUserLatentParamModel(
                        userName = "",
                        checkState = UserLatentCheckState.APPROVED,
                    )
                ),
                requests,
            )
            assertEquals(
                UserLatentCheckState.APPROVED,
                viewModel.uiState.value.customerCheckState,
            )
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
