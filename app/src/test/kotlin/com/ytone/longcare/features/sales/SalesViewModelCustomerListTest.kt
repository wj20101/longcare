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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                        pageIndex = 1,
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
                        pageIndex = 1,
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

    @Test
    fun `next pages append unique customers and stop after an empty page`() =
        runTest {
            val requests = mutableListOf<SearchUserLatentParamModel>()
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { searchUserLatentList(any()) } answers {
                        val request = firstArg<SearchUserLatentParamModel>()
                        requests += request
                        when (request.pageIndex) {
                            1 ->
                                ApiResult.Success(
                                    listOf(
                                        UserLatentListModel(id = 1, userName = "客户一"),
                                        UserLatentListModel(id = 2, userName = "客户二"),
                                    )
                                )

                            2 ->
                                ApiResult.Success(
                                    listOf(
                                        UserLatentListModel(id = 2, userName = "客户二"),
                                        UserLatentListModel(id = 3, userName = "客户三"),
                                    )
                                )

                            else -> ApiResult.Success(emptyList())
                        }
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.searchCustomers("", UserLatentCheckState.ALL)
            viewModel.loadNextCustomerPage()

            assertEquals(
                listOf(1, 2, 3),
                viewModel.uiState.value.customers.map(UserLatentListModel::id),
            )
            assertEquals(2, viewModel.uiState.value.customerPageIndex)
            assertTrue(viewModel.uiState.value.canLoadMoreCustomers)

            viewModel.loadNextCustomerPage()
            viewModel.loadNextCustomerPage()

            assertEquals(listOf(1, 2, 3), requests.map { it.pageIndex })
            assertEquals(3, viewModel.uiState.value.customerPageIndex)
            assertFalse(viewModel.uiState.value.canLoadMoreCustomers)
        }

    @Test
    fun `failed next page is retried without clearing loaded customers`() =
        runTest {
            var secondPageAttempts = 0
            val requests = mutableListOf<SearchUserLatentParamModel>()
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { searchUserLatentList(any()) } answers {
                        val request = firstArg<SearchUserLatentParamModel>()
                        requests += request
                        if (request.pageIndex == 1) {
                            ApiResult.Success(
                                listOf(UserLatentListModel(id = 1, userName = "客户一"))
                            )
                        } else if (secondPageAttempts++ == 0) {
                            ApiResult.Failure(code = 500, message = "加载失败")
                        } else {
                            ApiResult.Success(
                                listOf(UserLatentListModel(id = 2, userName = "客户二"))
                            )
                        }
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.searchCustomers("", UserLatentCheckState.ALL)
            viewModel.loadNextCustomerPage()

            assertEquals(listOf(1), viewModel.uiState.value.customers.map { it.id })
            assertEquals(1, viewModel.uiState.value.customerPageIndex)
            assertEquals("加载失败", viewModel.uiState.value.customerLoadMoreErrorMessage)

            viewModel.loadNextCustomerPage()

            assertEquals(listOf(1, 2, 2), requests.map { it.pageIndex })
            assertEquals(listOf(1, 2), viewModel.uiState.value.customers.map { it.id })
            assertEquals(2, viewModel.uiState.value.customerPageIndex)
            assertEquals(null, viewModel.uiState.value.customerLoadMoreErrorMessage)
        }

    @Test
    fun `new search cancels an unfinished page and rejects its stale result`() =
        runTest {
            val oldPageResult =
                CompletableDeferred<ApiResult<List<UserLatentListModel>>>()
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { searchUserLatentList(any()) } coAnswers {
                        val request = firstArg<SearchUserLatentParamModel>()
                        when {
                            request.userName == "旧条件" && request.pageIndex == 1 ->
                                ApiResult.Success(
                                    listOf(UserLatentListModel(id = 1, userName = "旧客户"))
                                )

                            request.userName == "旧条件" -> oldPageResult.await()
                            else ->
                                ApiResult.Success(
                                    listOf(UserLatentListModel(id = 9, userName = "新客户"))
                                )
                        }
                    }
                }
            val viewModel = createViewModel(repository)

            viewModel.searchCustomers("旧条件", UserLatentCheckState.ALL)
            viewModel.loadNextCustomerPage()
            viewModel.searchCustomers("新条件", UserLatentCheckState.ALL)
            oldPageResult.complete(
                ApiResult.Success(
                    listOf(UserLatentListModel(id = 2, userName = "迟到的旧客户"))
                )
            )
            advanceUntilIdle()

            assertEquals(listOf(9), viewModel.uiState.value.customers.map { it.id })
            assertEquals("新条件", viewModel.uiState.value.customerSearchKeyword)
            assertEquals(1, viewModel.uiState.value.customerPageIndex)
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
