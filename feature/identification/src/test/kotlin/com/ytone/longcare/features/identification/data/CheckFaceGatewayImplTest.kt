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
import kotlinx.coroutines.awaitCancellation
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
    fun `failed comparison is returned without a second face lookup`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Failure(code = 400, message = "人脸不匹配"),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(
            CheckFaceRemoteResult.Rejected(code = 400, message = "人脸不匹配"),
        )
        assertThat(repository.getFaceCallCount).isEqualTo(0)
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
    fun `network failure remains retryable without a second face lookup`() = runTest {
        val repository = FakeIdentificationRepository(
            checkResult = ApiResult.Exception(IOException("offline")),
        )
        val gateway = CheckFaceGatewayImpl(repository)

        val result = gateway.checkFace(orderId = 123, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceRemoteResult.NetworkError)
        assertThat(repository.getFaceCallCount).isEqualTo(0)
    }

    @Test
    fun `comparison has a bounded total timeout`() = runTest {
        val repository = object : IdentificationRepository {
            override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
                error("Unexpected call")

            override suspend fun getFace(): ApiResult<FaceResultModel> =
                error("Unexpected call")

            override suspend fun checkFace(
                checkFaceParamModel: CheckFaceParamModel,
            ): ApiResult<Unit> = awaitCancellation()
        }

        val result = CheckFaceGatewayImpl(repository).checkFace(
            orderId = 123,
            faceImageBase64 = "ZmFjZQ==",
        )

        assertThat(result).isEqualTo(CheckFaceRemoteResult.NetworkError)
    }

    private class FakeIdentificationRepository(
        private val checkResult: ApiResult<Unit>,
    ) : IdentificationRepository {
        var receivedRequest: CheckFaceParamModel? = null
        var getFaceCallCount = 0

        override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
            error("Unexpected call")

        override suspend fun getFace(): ApiResult<FaceResultModel> {
            getFaceCallCount += 1
            error("CheckFace must not re-query GetFace")
        }

        override suspend fun checkFace(
            checkFaceParamModel: CheckFaceParamModel,
        ): ApiResult<Unit> {
            receivedRequest = checkFaceParamModel
            return checkResult
        }
    }
}
