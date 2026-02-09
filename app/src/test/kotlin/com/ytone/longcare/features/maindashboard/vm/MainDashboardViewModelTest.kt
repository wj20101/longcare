package com.ytone.longcare.features.maindashboard.vm

import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainDashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val systemConfigManager = mockk<SystemConfigManager>(relaxed = true)
    private val unifiedOrderRepository = mockk<UnifiedOrderRepository>(relaxed = true)
    private val toastHelper = mockk<ToastHelper>(relaxed = true)

    private fun createViewModel(): MainDashboardViewModel {
        return MainDashboardViewModel(
            systemConfigManager = systemConfigManager,
            unifiedOrderRepository = unifiedOrderRepository,
            toastHelper = toastHelper
        )
    }

    @Test
    fun `loadCompanyName should update state and avoid duplicate load`() = runTest {
        coEvery { systemConfigManager.getCompanyName() } returns "LongCare"
        val viewModel = createViewModel()

        viewModel.loadCompanyName()
        advanceUntilIdle()
        viewModel.loadCompanyName()
        advanceUntilIdle()

        assertEquals("LongCare", viewModel.companyName.value)
        coVerify(exactly = 1) { systemConfigManager.getCompanyName() }
    }

    @Test
    fun `buildServiceCountdownNavigationData should use cached order and default to all projects when no selection`() = runTest {
        val orderId = 1001L
        val orderKey = OrderKey(orderId = orderId, planId = 0)
        val orderInfo = createOrderInfo(orderId, listOf(11, 22, 33))
        every { unifiedOrderRepository.getCachedOrderInfo(orderKey) } returns orderInfo
        coEvery { unifiedOrderRepository.getSelectedProjectIds(orderKey) } returns emptyList()
        val viewModel = createViewModel()

        val result = viewModel.buildServiceCountdownNavigationData(orderId = orderId)

        assertNotNull(result)
        assertEquals(orderId, result!!.orderParams.orderId)
        assertEquals(listOf(11, 22, 33), result.projectIdList)
        coVerify(exactly = 0) { unifiedOrderRepository.getOrderInfo(any()) }
    }

    @Test
    fun `buildServiceCountdownNavigationData should fetch from api when cache miss and keep selected projects`() = runTest {
        val orderId = 1002L
        val orderKey = OrderKey(orderId = orderId, planId = 0)
        val orderInfo = createOrderInfo(orderId, listOf(1, 2, 3))
        every { unifiedOrderRepository.getCachedOrderInfo(orderKey) } returns null
        coEvery { unifiedOrderRepository.getOrderInfo(orderKey) } returns ApiResult.Success(orderInfo)
        coEvery { unifiedOrderRepository.getSelectedProjectIds(orderKey) } returns listOf(2, 3)
        val viewModel = createViewModel()

        val result = viewModel.buildServiceCountdownNavigationData(orderId = orderId)

        assertNotNull(result)
        assertEquals(listOf(2, 3), result!!.projectIdList)
    }

    @Test
    fun `buildServiceCountdownNavigationData should return null and toast on api failure`() = runTest {
        val orderId = 1003L
        val orderKey = OrderKey(orderId = orderId, planId = 0)
        every { unifiedOrderRepository.getCachedOrderInfo(orderKey) } returns null
        coEvery { unifiedOrderRepository.getOrderInfo(orderKey) } returns ApiResult.Failure(
            code = 500,
            message = "server error"
        )
        val viewModel = createViewModel()

        val result = viewModel.buildServiceCountdownNavigationData(orderId = orderId)

        assertNull(result)
        verify(exactly = 1) { toastHelper.showShort("server error") }
    }

    private fun createOrderInfo(orderId: Long, projectIds: List<Int>): ServiceOrderInfoModel {
        return ServiceOrderInfoModel(
            orderId = orderId,
            projectList = projectIds.map { projectId ->
                ServiceProjectM(
                    projectId = projectId,
                    projectName = "P$projectId",
                    serviceTime = 30
                )
            }
        )
    }
}
