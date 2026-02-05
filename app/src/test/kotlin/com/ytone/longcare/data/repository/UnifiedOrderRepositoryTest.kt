package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.OrderInfoParamModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.api.response.UserInfoM
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.entity.OrderEntity
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.Response
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
    private lateinit var eventBus: AppEventBus
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
        eventBus = mockk(relaxed = true)
        
        repository = UnifiedOrderRepository(
            apiService,
            testDispatcher,
            eventBus,
            orderDao,
            elderInfoDao,
            localStateDao,
            projectDao
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
        coEvery { apiService.getOrderInfo(any()) } returns Response(1000, "OK", apiModel)
        
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
        coEvery { apiService.getOrderInfo(any()) } returns Response(1000, "OK", freshModel)
        
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
    fun `updateFaceVerification should update LocalStateDao`() = runTest(testDispatcher) {
        // Given
        val orderKey = OrderKey(12345L, 0)
        
        // When
        repository.updateFaceVerification(orderKey, true)
        
        // Then
        coVerify(exactly = 1) { localStateDao.updateFaceVerification(12345L, true, any()) }
    }
}
