package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.GetCheckTokenParamModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : SaleRepository {

    override suspend fun getCheckToken(
        customerId: Int,
        checkDeviceId: String,
    ): ApiResult<CheckTokenModel> =
        apiService.getCheckToken(
            GetCheckTokenParamModel(
                id = customerId,
                checkDeviceId = checkDeviceId,
            )
        )

    override suspend fun addUserLatent(
        request: AddUserLatentParamModel,
    ): ApiResult<AddUserLatentResultModel> =
        apiService.addUserLatent(request)

    override suspend fun getRecentUserLatentList(): ApiResult<List<UserLatentListModel>> =
        apiService.getRecentUserLatentList()

    override suspend fun searchUserLatentList(
        request: SearchUserLatentParamModel,
    ): ApiResult<List<UserLatentListModel>> =
        apiService.searchUserLatentList(request)

    override suspend fun getUserLatentDetail(
        customerId: Int,
    ): ApiResult<UserLatentDetailModel> =
        apiService.getUserLatentDetail(customerId)
}
