package com.ytone.longcare.features.identification.data

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.CheckFaceRemoteResult
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CheckFaceGatewayImplTest {
    @Test
    fun `check face delegates exact request model and maps success`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Success(Unit),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.Success)
        assertThat(repository.receivedRequest).isEqualTo(
            CheckFaceParamModel(orderId = 123, faceImg = "ZmFjZQ=="),
        )
        assertThat(repository.getFaceCallCount).isEqualTo(0)
    }

    @Test
    fun `failed comparison is preserved when registered face is available`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(code = 400, message = "人脸不匹配"),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(
            CheckFaceRemoteResult.Rejected(code = 400, message = "人脸不匹配"),
        )
        assertThat(repository.getFaceCallCount).isEqualTo(1)
    }

    @Test
    fun `blank registered face after failed comparison means face is missing`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(code = 400, message = "校验失败"),
            getFaceResult = ApiResult.Success(FaceResultModel(faceImgUrl = "")),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.MissingRegisteredFace)
    }

    @Test
    fun `unavailable registered face means face is missing by business policy`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(code = 400, message = "校验失败"),
            getFaceResult = ApiResult.Exception(IOException("offline")),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.MissingRegisteredFace)
    }

    @Test
    fun `check face session failure short circuits registered face lookup`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(
                code = SessionInvalidationCode.INVALID_SESSION,
                message = "登录已失效",
            ),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.SessionInvalidated)
        assertThat(repository.getFaceCallCount).isEqualTo(0)
    }

    @Test
    fun `registered face session failure remains session invalidation`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(code = 400, message = "校验失败"),
            getFaceResult = ApiResult.Failure(
                code = SessionInvalidationCode.SESSION_EXPIRED,
                message = "登录已过期",
            ),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.SessionInvalidated)
    }

    @Test
    fun `network failure remains retryable when registered face is available`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Exception(IOException("offline")),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.NetworkError)
    }

    private class FakeIdentificationRepository(
        private val checkResult: ApiResult<Unit>,
        private val getFaceResult: ApiResult<FaceResultModel> = ApiResult.Success(
            FaceResultModel(faceImgUrl = "https://face.url"),
        ),
    ) : IdentificationRepository {
        var receivedRequest: CheckFaceParamModel? = null
        var getFaceCallCount = 0

        override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
            error("Unexpected call")

        override suspend fun getFace(): ApiResult<FaceResultModel> {
            getFaceCallCount += 1
            return getFaceResult
        }

        override suspend fun checkFace(
            checkFaceParamModel: CheckFaceParamModel,
        ): ApiResult<Unit> {
            receivedRequest = checkFaceParamModel
            return checkResult
        }
    }
}
