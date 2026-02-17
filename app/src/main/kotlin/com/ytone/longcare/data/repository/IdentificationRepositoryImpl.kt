package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.identification.IdentificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class IdentificationRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : IdentificationRepository {

    override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.setFace(setFaceParamModel)
        }
    }

    override suspend fun getFace(): ApiResult<FaceResultModel> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getFace()
        }
    }
}