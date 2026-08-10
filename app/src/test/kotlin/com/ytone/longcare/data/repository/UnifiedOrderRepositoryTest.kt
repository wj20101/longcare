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
import io.mockk.*
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
        
        repository = UnifiedOrderRepository(
            apiService = apiService,
            runtimeConfigProvider = runtimeConfigProvider,
            orderDao = orderDao,
            orderElderInfoDao = elderInfoDao,
            orderLocalStateDao = localStateDao,
            orderProjectDao = projectDao,
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
        coVerify(exactly = 1) { apiService.getOrderInfo(match { it.orderId == 12345L }) }
        
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
        repository.updateCachedOrderInfo(orderKey, cachedModel)
        
        // When
        val result = repository.getOrderInfo(orderKey, forceRefresh = false)
        
        // Then
        assertTrue(result is ApiResult.Success)
        assertEquals(cachedModel, (result as ApiResult.Success).data)
        
        // Verify API NOT called
        coVerify(exactly = 0) { apiService.getOrderInfo(any()) }
    }

    @Test
    fun `getOrderInfo should force refresh from API`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 1)
        val cachedModel = ServiceOrderInfoModel(orderId = 12345L, state = 2)
        val freshModel = ServiceOrderInfoModel(orderId = 12345L, state = 3)
        
        // Pre-populate cache
        repository.updateCachedOrderInfo(orderKey, cachedModel)
        
        // Mock API success
        coEvery { apiService.getOrderInfo(any()) } returns ApiResult.Success(freshModel)
        
        // When
        val result = repository.getOrderInfo(orderKey, forceRefresh = true)
        
        // Then
        assertTrue(result is ApiResult.Success)
        assertEquals(freshModel, (result as ApiResult.Success).data)
        
        // Verify API called
        coVerify(exactly = 1) { apiService.getOrderInfo(any()) }
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
}
