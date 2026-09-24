package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.EndOderInfo
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import com.ytone.longcare.common.utils.KLogger

@OptIn(ExperimentalCoroutinesApi::class)
class NfcOrderCompletionTest {
    @Before fun disablePlatformLogging() { KLogger.updateConfig { enabled = false } }
    private val order = OrderKey(7, 2)
    private val repository = mockk<OrderRepository>()
    private val local = mockk<OrderDetailRepository>(relaxed = true)
    private val images = mockk<OrderImageRepository>(relaxed = true)
    private val system = mockk<ServiceCountdownSystemGateway>(relaxed = true)
    private val lifecycle = mockk<ServiceOrderLifecycle>(relaxed = true)
    private val completion = NfcOrderCompletionDelegate(local, images, system)
    private val state = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)

    private suspend fun end() = executeEndOrderRequest(lifecycle, repository, completion, state,
        order, "test-tag", listOf(1), listOf("before"), listOf("after"), emptyList(),
        "", "", 1, NfcUserMessages("网络异常", "详情失败", "定位失败"))

    @Test fun successPublishesOnlyAfterCleanupAndKeepsSummary() = runTest {
        coEvery { repository.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            ApiResult.Success(EndOrderResultModel(60))
        val release = CompletableDeferred<Unit>()
        coEvery { local.updateSelectedProjects(order, emptyList()) } coAnswers { release.await() }
        coEvery { local.getCachedOrderInfo(order) } returns ServiceOrderInfoModel(orderId = 7,
            userInfo = UserInfoM(name = "测试客户"),
            projectList = listOf(ServiceProjectM(projectId = 1, projectName = "护理项目")))
        val job = launch { end() }
        runCurrent()
        assertFalse(state.value is NfcSignInUiState.Success)
        release.complete(Unit)
        job.join()
        assertEquals(60, (state.value as NfcSignInUiState.Success).endOrderSuccessData?.trueServiceTime)
        coVerifyOrder {
            local.endLocalService(order)
            local.updateSelectedProjects(order, emptyList())
            images.deleteImagesByOrderId(order)
        }
        val summary = completion.buildServiceCompleteDataFromCache(order, EndOderInfo(projectIdList = listOf(1)), 60)
        assertEquals("测试客户", summary.clientName)
        assertEquals("护理项目", summary.serviceContent)
    }

    @Test fun leavingPageDoesNotCancelAlreadyStartedSuccessCleanup() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { local.endLocalService(order) } coAnswers { release.await() }
        val job = launch { completion.cleanupResources(order) }
        runCurrent()
        job.cancel()
        release.complete(Unit)
        job.join()
        coVerify(exactly = 1) { local.updateSelectedProjects(order, emptyList()) }
        coVerify(exactly = 1) { images.deleteImagesByOrderId(order) }
    }

    @Test fun failedEndRequestPreservesRetryData() = runTest {
        coEvery { repository.endOrder(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            ApiResult.Failure(400, "暂不能结束")
        end()
        assertTrue(state.value is NfcSignInUiState.Error)
        coVerify(exactly = 0) { local.endLocalService(any()) }
        coVerify(exactly = 0) { local.updateSelectedProjects(any(), any()) }
        coVerify(exactly = 0) { images.deleteImagesByOrderId(any()) }
        verify(exactly = 0) { lifecycle.onOrderEnded(any()) }
    }
}
