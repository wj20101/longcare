package com.ytone.longcare.features.identification.data

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CheckFaceGatewayImplTest {
    @Test
    fun `check face delegates exact request model and maps success`() = runTest {
        val repository = FakeIdentificationRepository(ApiResult.Success(Unit))
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.Success)
        assertThat(repository.receivedRequest).isEqualTo(
            CheckFaceParamModel(orderId = 123, faceImg = "ZmFjZQ=="),
        )
    }

    @Test
    fun `check face maps api failure message`() = runTest {
        val repository = FakeIdentificationRepository(
            ApiResult.Failure(code = 400, message = "人脸不匹配"),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.Rejected("人脸不匹配"))
    }

    private class FakeIdentificationRepository(
        private val checkResult: ApiResult<Unit>,
    ) : IdentificationRepository {
        var receivedRequest: CheckFaceParamModel? = null

        override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
            error("Unexpected call")

        override suspend fun getFace(): ApiResult<FaceResultModel> = error("Unexpected call")

        override suspend fun checkFace(
            checkFaceParamModel: CheckFaceParamModel,
        ): ApiResult<Unit> {
            receivedRequest = checkFaceParamModel
            return checkResult
        }
    }
}
