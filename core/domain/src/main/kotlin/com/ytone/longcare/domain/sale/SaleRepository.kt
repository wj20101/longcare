package com.ytone.longcare.domain.sale

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.ToDoNumResultModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel

/**
 * 销售与俏郎中检测相关接口。
 */
interface SaleRepository {
    suspend fun getCheckToken(
        customerId: Int,
        checkDeviceId: String,
    ): ApiResult<CheckTokenModel>

    suspend fun addUserLatent(
        request: AddUserLatentParamModel,
    ): ApiResult<AddUserLatentResultModel>

    suspend fun getRecentUserLatentList(): ApiResult<List<UserLatentListModel>>

    suspend fun getToDoCount(): ApiResult<ToDoNumResultModel>

    suspend fun getToDoList(): ApiResult<List<ToDoResultModel>>

    suspend fun searchUserLatentList(
        request: SearchUserLatentParamModel,
    ): ApiResult<List<UserLatentListModel>>

    suspend fun getUserLatentDetail(
        customerId: Int,
    ): ApiResult<UserLatentDetailModel>
}
