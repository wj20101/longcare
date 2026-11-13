package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.model.*
import com.ytone.longcare.model.result.ApiResult
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NfcBindingCacheTest {
    private val api = mockk<LongCareApiService>()
    private val orderDao = mockk<OrderDao>(relaxed = true)
    private val key = OrderKey(123, 1)
    private val unbound = ServiceOrderInfoModel(orderId = 123, userInfo = UserInfoM(lng = "", lat = ""))
    private val bound = unbound.copy(userInfo = UserInfoM(lng = "121.0", lat = "31.0"))
    private lateinit var details: UnifiedOrderRepository
    private lateinit var orders: OrderRepositoryImpl
    private val session = UserSessionTracker()
    private var server = unbound

    @Before fun setup() {
        val config = mockk<RuntimeConfigProvider> { every { isDebug } returns false }
        session.observe(User(userId = 1, token = "test"))
        details = UnifiedOrderRepository(api, config, orderDao, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), session)
        orders = OrderRepositoryImpl(api, details, session)
        coEvery { api.getOrderInfo(any()) } coAnswers { ApiResult.Success(server) }
        coEvery { api.bindLocation(any()) } coAnswers { server = bound; ApiResult.Success(Unit) }
    }

    @Test fun `success invalidates all plans for the order while preserving other orders`() = runTest {
        val otherPlan = key.copy(planId = 2)
        val otherOrder = OrderKey(456)
        listOf(key, otherPlan, otherOrder).forEach { details.getOrderInfo(it, true) }
        orders.bindLocation(123, "tag", "121.0", "31.0")
        assertNull(details.getCachedOrderInfo(key))
        assertNull(details.getCachedOrderInfo(otherPlan))
        assertNotNull(details.getCachedOrderInfo(otherOrder))
        assertEquals(bound, (details.getOrderInfo(key) as ApiResult.Success).data)
    }

    @Test fun `lost response cannot leave the pre-bind cache as the next read result`() = runTest {
        details.getOrderInfo(key, true)
        coEvery { api.bindLocation(any()) } coAnswers { server = bound; ApiResult.Exception(IOException("response lost")) }
        assertTrue(orders.bindLocation(123, "tag", "121.0", "31.0") is ApiResult.Exception)
        assertNull(details.getCachedOrderInfo(key))
        assertEquals(bound, (details.getOrderInfo(key) as ApiResult.Success).data)
        coVerify(exactly = 2) { api.getOrderInfo(any()) }
    }

    @Test fun `cancelled binding also invalidates because the server outcome is unknown`() = runTest {
        details.getOrderInfo(key, true)
        coEvery { api.bindLocation(any()) } throws CancellationException("left page")
        try { orders.bindLocation(123, "tag", "121.0", "31.0"); fail("Cancellation must propagate") }
        catch (cancelled: CancellationException) { assertEquals("left page", cancelled.message) }
        assertNull(details.getCachedOrderInfo(key))
    }

    @Test fun `late pre-bind detail is neither returned as current nor written to Room or cache`() = runTest {
        val oldResponse = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { api.getOrderInfo(any()) } coAnswers { oldResponse.await() }
        val oldRead = async { details.getOrderInfo(key, true) }
        runCurrent()
        orders.bindLocation(123, "tag", "121.0", "31.0")
        oldResponse.complete(ApiResult.Success(unbound))
        assertTrue(oldRead.await() is ApiResult.Exception)
        assertNull(details.getCachedOrderInfo(key))
        coVerify(exactly = 0) { orderDao.insertOrUpdate(any()) }
        coEvery { api.getOrderInfo(any()) } returns ApiResult.Success(bound)
        assertEquals(bound, (details.getOrderInfo(key) as ApiResult.Success).data)
    }

    @Test fun `invalidation during Room persistence still prevents old cache refill and success`() = runTest {
        val enteredRoom = CompletableDeferred<Unit>()
        val releaseRoom = CompletableDeferred<Unit>()
        coEvery { orderDao.insertOrUpdate(any()) } coAnswers { enteredRoom.complete(Unit); releaseRoom.await(); 123L }
        val oldRead = async { details.getOrderInfo(key, true) }
        enteredRoom.await()
        orders.bindLocation(123, "tag", "121.0", "31.0")
        releaseRoom.complete(Unit)
        assertTrue(oldRead.await() is ApiResult.Exception)
        assertNull(details.getCachedOrderInfo(key))
    }

    @Test fun `late old account binding cannot evict new account cache or invalidate its current detail read`() = runTest {
        val oldBinding = CompletableDeferred<ApiResult<Unit>>()
        coEvery { api.bindLocation(any()) } coAnswers { oldBinding.await() }
        val binding = async { orders.bindLocation(123, "old-tag", "121.0", "31.0") }
        runCurrent()
        session.observe(User(userId = 2, token = "second"))
        val currentDetail = bound.copy(userInfo = bound.userInfo?.copy(name = "Current account"))
        coEvery { api.getOrderInfo(any()) } returns ApiResult.Success(currentDetail)
        details.getOrderInfo(key, true)
        val currentResponse = CompletableDeferred<ApiResult<ServiceOrderInfoModel>>()
        coEvery { api.getOrderInfo(any()) } coAnswers { currentResponse.await() }
        val currentRead = async { details.getOrderInfo(key, true) }
        runCurrent()
        oldBinding.complete(ApiResult.Success(Unit))
        binding.await()
        currentResponse.complete(ApiResult.Success(currentDetail))
        assertEquals(currentDetail, details.getCachedOrderInfo(key))
        assertEquals(currentDetail, (currentRead.await() as ApiResult.Success).data)
    }
}
