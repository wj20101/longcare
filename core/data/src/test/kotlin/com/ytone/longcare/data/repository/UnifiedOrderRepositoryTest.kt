package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.UserInfoM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.entity.OrderElderInfoEntityDb
import com.ytone.longcare.data.database.entity.OrderEntityDb
import com.ytone.longcare.model.OrderEntity
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.User
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UnifiedOrderRepositoryTest {

    private lateinit var apiService: LongCareApiService
    private lateinit var orderDao: OrderDao
    private lateinit var projectDao: OrderProjectDao
    private lateinit var imageDao: OrderImageDao
    private lateinit var elderInfoDao: OrderElderInfoDao
    private lateinit var localStateDao: OrderLocalStateDao
    private lateinit var runtimeConfigProvider: RuntimeConfigProvider
    private lateinit var repository: UnifiedOrderRepository
    private lateinit var session: UserSessionTracker

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        apiService = mockk()
        orderDao = mockk(relaxed = true)
        projectDao = mockk(relaxed = true)
        imageDao = mockk(relaxed = true)
        elderInfoDao = mockk(relaxed = true)
        localStateDao = mockk(relaxed = true)
        runtimeConfigProvider = mockk(relaxed = true)
        every { runtimeConfigProvider.isDebug } returns false
        session = UserSessionTracker().apply { observe(User(userId = 1, token = "first")) }
        
        repository = UnifiedOrderRepository(
            apiService = apiService,
            runtimeConfigProvider = runtimeConfigProvider,
            orderDao = orderDao,
            orderElderInfoDao = elderInfoDao,
            orderLocalStateDao = localStateDao,
            orderProjectDao = projectDao,
            session = session,
        )
    }

    @Test
    fun `getOrderInfo should fetch from API and cache if memory cache is empty`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 1)
        val apiModel = ServiceOrderInfoModel(
            orderId = 12345L,
            state = 1,
            userInfo = UserInfoM(name = "Test User")
        )
        
        // Mock API success
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(apiModel)
        
        // When
        val result = repository.getOrderInfo(orderKey, forceRefresh = false)
        
        // Then
        assertTrue(result is ApiResult.Success)
        assertEquals(apiModel, (result as ApiResult.Success).data)
        
        // Verify API called
        coVerify(exactly = 1) { apiService.getOrderInfo(OrderInfoParamModel(12345L, 1)) }
        
        // Verify Saved to DB (Side effect)
        coVerify(exactly = 1) { orderDao.insertOrUpdate(any()) }
        
        // Verify cached in memory (by calling getCachedOrderInfo)
        assertEquals(apiModel, repository.getCachedOrderInfo(orderKey))
    }

    @Test
    fun `getOrderInfo should return memory cache if available`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 1)
        val cachedModel = ServiceOrderInfoModel(orderId = 12345L, state = 2)
        
        // Pre-populate cache
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(cachedModel)
        repository.getOrderInfo(orderKey, forceRefresh = false)
        
        // When
        val result = repository.getOrderInfo(orderKey, forceRefresh = false)
        
        // Then
        assertTrue(result is ApiResult.Success)
        assertEquals(cachedModel, (result as ApiResult.Success).data)
        
        // The second read reuses the first network result.
        coVerify(exactly = 1) { apiService.getOrderInfo(any()) }
    }

    @Test
    fun `getOrderInfo should force refresh from API`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 1)
        val cachedModel = ServiceOrderInfoModel(orderId = 12345L, state = 2)
        val freshModel = ServiceOrderInfoModel(orderId = 12345L, state = 3)
        
        // Pre-populate cache
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(cachedModel)
        repository.getOrderInfo(orderKey, forceRefresh = false)
        
        // Mock API success
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(freshModel)
        
        // When
        val result = repository.getOrderInfo(orderKey, forceRefresh = true)
        
        // Then
        assertTrue(result is ApiResult.Success)
        assertEquals(freshModel, (result as ApiResult.Success).data)
        
        // Verify API called
        coVerify(exactly = 2) { apiService.getOrderInfo(any()) }
        // Verify cache updated
        assertEquals(freshModel, repository.getCachedOrderInfo(orderKey))
    }

    @Test
    fun `getOrderInfo should persist using request orderId when payload orderId mismatches`() = runTest(testDispatcher) {
        val orderKey = OrderKey(12345L, 1)
        val apiModel = ServiceOrderInfoModel(
            orderId = 0L,
            state = 1,
            userInfo = UserInfoM(userId = 99, name = "Test User")
        )
        val insertedOrderSlot = slot<OrderEntityDb>()
        val insertedElderSlot = slot<OrderElderInfoEntityDb>()

        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(apiModel)
        coEvery { orderDao.insertOrUpdate(capture(insertedOrderSlot)) } returns orderKey.orderId
        coEvery { elderInfoDao.insertOrUpdate(capture(insertedElderSlot)) } returns orderKey.orderId
        coEvery { projectDao.getSelectedProjectIds(orderKey.orderId) } returns emptyList()
        coEvery { localStateDao.getByOrderId(orderKey.orderId) } returns null
        coEvery { localStateDao.insertOrUpdate(any()) } returns orderKey.orderId

        val result = repository.getOrderInfo(orderKey, forceRefresh = false)

        assertTrue(result is ApiResult.Success)
        assertEquals(orderKey.orderId, insertedOrderSlot.captured.orderId)
        assertEquals(orderKey.orderId, insertedElderSlot.captured.orderId)
    }
    
    @Test
    fun `updateFaceVerification should update LocalStateDao`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 0)
        
        // When
        repository.updateFaceVerification(orderKey, true)
        
        // Then
        coVerify(exactly = 1) { localStateDao.updateFaceVerification(12345L, true, any()) }
    }

    @Test
    fun `logout clears cached orders and rejects reads until the next session`() = runTest {
        val key = OrderKey(42)
        val first = ServiceOrderInfoModel(orderId = 42, state = 1)
        val fresh = first.copy(state = 2)
        coEvery { apiService.getOrderInfo(any()) } returnsMany listOf(ApiResult.Success(first), ApiResult.Success(fresh))
        repository.getOrderInfo(key, false)
        session.observe(User(userId = 1, token = "first", userName = "renamed"))
        assertEquals(first, repository.getCachedOrderInfo(key))

        session.beginChange()
        session.finishChange(null)
        assertNull(repository.getCachedOrderInfo(key))
        org.junit.Assert.assertTrue(
            "Logged-out requests must be rejected",
            runCatching { repository.getOrderInfo(key, false) }.exceptionOrNull() is CancellationException,
        )
        session.beginChange(User(userId = 1, token = "first"))
        session.finishChange(User(userId = 1, token = "first"))
        assertEquals(fresh, (repository.getOrderInfo(key, false) as ApiResult.Success).data)
        coVerify(exactly = 2) { apiService.getOrderInfo(any()) }
    }

    @Test
    fun `old response cannot overwrite a new session cache or reach Room`() = runTest {
        val key = OrderKey(42)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = ServiceOrderInfoModel(orderId = 42, state = 1)
        val fresh = old.copy(state = 2)
        coEvery { apiService.getOrderInfo(any()) } coAnswers {
            started.complete(Unit)
            release.await()
            ApiResult.Success(old)
        }
        val pending = async { repository.getOrderInfo(key, false) }
        started.await()
        session.beginChange(User(userId = 2, token = "second"))
        session.finishChange(User(userId = 2, token = "second"))
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(fresh)
        repository.getOrderInfo(key, false)
        release.complete(Unit)
        org.junit.Assert.assertTrue(
            "Old response must be rejected",
            runCatching { pending.await() }.exceptionOrNull() is CancellationException,
        )
        assertEquals(fresh, repository.getCachedOrderInfo(key))
        coVerify(exactly = 1) { orderDao.insertOrUpdate(any()) }
    }
}
