package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.SetFaceParamModel
import javax.inject.Inject

class IdentificationRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : IdentificationRepository {

    override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
        apiService.setFace(setFaceParamModel)

    override suspend fun getFace(): ApiResult<FaceResultModel> = apiService.getFace()

    override suspend fun checkFace(checkFaceParamModel: CheckFaceParamModel): ApiResult<Unit> =
        apiService.checkFace(checkFaceParamModel)
}
