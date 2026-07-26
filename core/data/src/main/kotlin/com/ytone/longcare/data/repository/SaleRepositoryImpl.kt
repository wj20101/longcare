package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.GetCheckTokenParamModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus,
) : SaleRepository {

    override suspend fun getCheckToken(
        customerId: Int,
        checkDeviceId: String,
    ): ApiResult<CheckTokenModel> =
        safeApiCall(ioDispatcher, eventBus) {
            apiService.getCheckToken(
                GetCheckTokenParamModel(
                    id = customerId,
                    checkDeviceId = checkDeviceId,
                )
            )
        }

    override suspend fun addUserLatent(
        request: AddUserLatentParamModel,
    ): ApiResult<AddUserLatentResultModel> =
        safeApiCall(ioDispatcher, eventBus) {
            apiService.addUserLatent(request)
        }

    override suspend fun getRecentUserLatentList(): ApiResult<List<UserLatentListModel>> =
        safeApiCall(ioDispatcher, eventBus) {
            apiService.getRecentUserLatentList()
        }

    override suspend fun searchUserLatentList(
        request: SearchUserLatentParamModel,
    ): ApiResult<List<UserLatentListModel>> =
        safeApiCall(ioDispatcher, eventBus) {
            apiService.searchUserLatentList(request)
        }

    override suspend fun getUserLatentDetail(
        customerId: Int,
    ): ApiResult<UserLatentDetailModel> =
        safeApiCall(ioDispatcher, eventBus) {
            apiService.getUserLatentDetail(customerId)
        }
}
