package com.ytone.longcare.features.sales

import android.content.Context
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.ToDoNumResultModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.common.text.ResourceTextResolver
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
class SalesViewModelToDoTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `to-do count and list are loaded from their dedicated endpoints`() =
        runTest {
            val reminders =
                listOf(
                    ToDoResultModel(
                        title = "上门评估",
                        content = "请联系客户确认时间",
                        createTime = "2026-08-01 09:00:00",
                    )
                )
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getToDoCount() } returns
                        ApiResult.Success(ToDoNumResultModel(num = 3))
                    coEvery { getToDoList() } returns
                        ApiResult.Success(reminders)
                }
            val viewModel = createViewModel(repository)

            viewModel.loadToDoCount()
            viewModel.loadToDoList()

            assertEquals(3, viewModel.uiState.value.toDoCount)
            assertEquals(reminders, viewModel.uiState.value.toDoItems)
            assertNull(viewModel.uiState.value.toDoCountErrorMessage)
            assertNull(viewModel.uiState.value.toDoListErrorMessage)
            assertFalse(viewModel.uiState.value.isToDoCountLoading)
            assertFalse(viewModel.uiState.value.isToDoListLoading)
        }

    @Test
    fun `to-do list failure keeps page retryable without global blocking`() =
        runTest {
            val repository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns
                        ApiResult.Success(emptyList())
                    coEvery { getToDoList() } returns
                        ApiResult.Failure(
                            code = 500,
                            message = "待办服务繁忙",
                        )
                }
            val viewModel = createViewModel(repository)

            viewModel.loadToDoList()

            assertEquals(
                "待办服务繁忙",
                viewModel.uiState.value.toDoListErrorMessage,
            )
            assertFalse(viewModel.uiState.value.isToDoListLoading)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    private fun createViewModel(repository: SaleRepository): SalesViewModel =
        SalesViewModel(
            saleRepository = repository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = UnusedPhotoCloudUploader,
            imagePipeline = testImagePipeline(mockk(relaxed = true)),
            evaluationDeviceGateway = mockk<SalesEvaluationDeviceGateway>(relaxed = true),
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            textResolver = ResourceTextResolver(mockk<Context>(relaxed = true)),
        )
}
