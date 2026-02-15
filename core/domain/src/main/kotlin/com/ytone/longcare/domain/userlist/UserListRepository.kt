package com.ytone.longcare.domain.userlist

import com.ytone.longcare.api.response.UserInfoModel
import com.ytone.longcare.api.response.UserOrderModel
import com.ytone.longcare.common.network.ApiResult

/**
 * 用户列表数据仓库接口
 */
interface UserListRepository {

    /**
     * 获取本月已服务的用户列表
     */
    suspend fun getHaveServiceUserList(): ApiResult<List<UserInfoModel>>

    /**
     * 获取本月未服务的用户列表
     */
    suspend fun getNoServiceUserList(): ApiResult<List<UserInfoModel>>

    /**
     * 获取用户服务记录列表
     * @param userId 用户ID
     */
    suspend fun getUserOrderList(userId: Long): ApiResult<List<UserOrderModel>>
}