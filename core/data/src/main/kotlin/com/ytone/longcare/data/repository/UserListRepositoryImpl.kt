package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.userlist.UserListRepository
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.model.UserOrderModel
import com.ytone.longcare.model.UserOrderParamModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserListRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : UserListRepository {

    /**
     * 获取本月已服务的用户列表
     */
    override suspend fun getHaveServiceUserList(): ApiResult<List<UserInfoModel>> =
        apiService.getHaveServiceUserList()

    /**
     * 获取本月未服务的用户列表
     */
    override suspend fun getNoServiceUserList(): ApiResult<List<UserInfoModel>> =
        apiService.getNoServiceUserList()

    /**
     * 获取用户服务记录列表
     * @param userId 用户ID
     */
    override suspend fun getUserOrderList(userId: Long): ApiResult<List<UserOrderModel>> =
        apiService.getUserOrderList(UserOrderParamModel(userId = userId.toInt()))
}
