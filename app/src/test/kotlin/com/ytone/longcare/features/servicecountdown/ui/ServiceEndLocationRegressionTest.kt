package com.ytone.longcare.features.servicecountdown.ui

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.ServiceTimeSnapshot
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceEndLocationRegressionTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `opening sign out selection and returning preserves the active service and deadline`() = runTest {
        val orderKey = OrderKey(orderId = 501L, planId = 0)
        val repository = mockk<OrderDetailRepository>(relaxed = true)
        val gateway = mockk<ServiceCountdownSystemGateway>(relaxed = true)
        val startedAt = System.currentTimeMillis() - 10_000L
        coEvery { repository.getOrderInfo(orderKey, any()) } returns ApiResult.Success(ServiceOrderInfoModel(
            orderId = orderKey.orderId,
            projectList = listOf(ServiceProjectM(projectId = 1, projectName = "护理", serviceTime = 2)),
        ))
        coEvery { repository.getLocalState(orderKey) } returns OrderLocalStateEntity(
            orderId = orderKey.orderId, localStartTimestamp = startedAt)
        val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
        val countdownViewModel = ServiceCountdownViewModel(repository, mockk(relaxed = true), lifecycle, gateway) {
            ServiceTimeSnapshot(System.currentTimeMillis(), 0L, null)
        }
        assertTrue(countdownViewModel.synchronizeCountdownSession(orderKey, listOf(1)).await())
        countdownViewModel.stopDisplayUpdates()
        var navigatedToConfirmation = false
        val actions = ServiceCountdownActions(
            onNavigateHomeAndClearStack = {},
            onNavigateToEndServiceSelection = { actualOrder, _, _ ->
                navigatedToConfirmation = actualOrder == orderKey
            },
            onNavigateToPhotoUpload = {},
            photoUploadResultFlow =
                MutableStateFlow<Map<ImageTaskType, List<ImageTask>>?>(null),
            clearPhotoUploadResult = {},
        )

        handleEndService(
            orderKey = orderKey,
            projectIdList = listOf(1, 2),
            actions = actions,
            endType = 2,
        )

        assertTrue(navigatedToConfirmation)
        assertEquals(ServiceCountdownState.RUNNING, countdownViewModel.countdownState.value)
        // 普通返回只恢复当前页，持久服务起点和上报会话不能在选择签退方式时提前结束。
        assertTrue(countdownViewModel.synchronizeCountdownSession(orderKey, listOf(1)).await())
        coVerify(exactly = 0) { repository.endLocalService(any()) }
        verify(exactly = 0) { gateway.cancelCountdownAlarmForOrder(any()) }
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
        verify(exactly = 2) {
            gateway.scheduleCountdownAlarm(orderKey, "护理", match { kotlin.math.abs(it.wallTimeMillis - (startedAt + 120_000L)) < 1_000L })
        }
        countdownViewModel.stopDisplayUpdates()
    }
}
