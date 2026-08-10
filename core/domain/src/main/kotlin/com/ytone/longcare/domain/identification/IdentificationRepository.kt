package com.ytone.longcare.domain.identification

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.SetFaceParamModel

interface IdentificationRepository {

    /**
     * 设置用户人脸信息
     * @param setFaceParamModel 人脸设置参数，包含人脸图片和图片URL
     * @return ApiResult<Unit> API调用结果
     */
    suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit>

    /**
     * 获取用户人脸地址
     */
    suspend fun getFace(): ApiResult<FaceResultModel>
}
