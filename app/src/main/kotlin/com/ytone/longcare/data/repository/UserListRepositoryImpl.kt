package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.UserOrderParamModel
import com.ytone.longcare.api.response.UserInfoModel
import com.ytone.longcare.api.response.UserOrderModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.userlist.UserListRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserListRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : UserListRepository {

    /**
     * 获取本月已服务的用户列表
     */
    override suspend fun getHaveServiceUserList(): ApiResult<List<UserInfoModel>> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getHaveServiceUserList()
        }
    }

    /**
     * 获取本月未服务的用户列表
     */
    override suspend fun getNoServiceUserList(): ApiResult<List<UserInfoModel>> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getNoServiceUserList()
        }
    }

    /**
     * 获取用户服务记录列表
     * @param userId 用户ID
     */
    override suspend fun getUserOrderList(userId: Long): ApiResult<List<UserOrderModel>> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getUserOrderList(UserOrderParamModel(userId = userId.toInt()))
        }
    }
}