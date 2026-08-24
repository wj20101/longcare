package com.ytone.longcare.features.identification.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CheckFaceUseCaseTest {
    @Test
    fun `valid request delegates exact documented values`() = runTest {
        val gateway = FakeCheckFaceGateway(CheckFaceRemoteResult.Success)
        val useCase = CheckFaceUseCase(gateway)

        val result = useCase.execute(orderId = 123L, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(CheckFaceResult.Success)
        assertThat(gateway.receivedOrderId).isEqualTo(123)
        assertThat(gateway.receivedFaceImageBase64).isEqualTo("ZmFjZQ==")
    }

    @Test
    fun `maximum documented int32 order id is accepted`() = runTest {
        val gateway = FakeCheckFaceGateway(CheckFaceRemoteResult.Success)
        val useCase = CheckFaceUseCase(gateway)

        val result = useCase.execute(
            orderId = Int.MAX_VALUE.toLong(),
            faceImageBase64 = "ZmFjZQ==",
        )

        assertThat(result).isEqualTo(CheckFaceResult.Success)
        assertThat(gateway.receivedOrderId).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `invalid order id is rejected before remote call`() = runTest {
        val gateway = FakeCheckFaceGateway(CheckFaceRemoteResult.Success)
        val useCase = CheckFaceUseCase(gateway)

        val result = useCase.execute(
            orderId = Int.MAX_VALUE.toLong() + 1L,
            faceImageBase64 = "ZmFjZQ==",
        )

        assertThat(result).isInstanceOf(CheckFaceResult.Error::class.java)
        assertThat(gateway.callCount).isEqualTo(0)
    }

    @Test
    fun `blank image is rejected before remote call`() = runTest {
        val gateway = FakeCheckFaceGateway(CheckFaceRemoteResult.Success)
        val useCase = CheckFaceUseCase(gateway)

        val result = useCase.execute(orderId = 123L, faceImageBase64 = "  ")

        assertThat(result).isInstanceOf(CheckFaceResult.Error::class.java)
        assertThat(gateway.callCount).isEqualTo(0)
    }

    @Test
    fun `remote error is preserved for presentation`() = runTest {
        val gateway = FakeCheckFaceGateway(
            CheckFaceRemoteResult.Rejected(
                code = 400,
                message = "人脸相似度不足",
            ),
        )
        val useCase = CheckFaceUseCase(gateway)

        val result = useCase.execute(orderId = 123L, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(
            CheckFaceResult.Error(
                CheckFaceFailure.Rejected(
                    code = 400,
                    serverMessage = "人脸相似度不足",
                ),
            ),
        )
    }

    @Test
    fun `missing registered face is preserved for terminal presentation`() = runTest {
        val useCase = CheckFaceUseCase(
            FakeCheckFaceGateway(CheckFaceRemoteResult.MissingRegisteredFace),
        )

        val result = useCase.execute(orderId = 123L, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(
            CheckFaceResult.Error(CheckFaceFailure.MissingRegisteredFace),
        )
    }

    @Test
    fun `session invalidation is preserved for global logout navigation`() = runTest {
        val useCase = CheckFaceUseCase(
            FakeCheckFaceGateway(CheckFaceRemoteResult.SessionInvalidated),
        )

        val result = useCase.execute(orderId = 123L, faceImageBase64 = "ZmFjZQ==")

        assertThat(result).isEqualTo(
            CheckFaceResult.Error(CheckFaceFailure.SessionInvalidated),
        )
    }

    private class FakeCheckFaceGateway(
        private val result: CheckFaceRemoteResult,
    ) : CheckFaceGateway {
        var callCount = 0
        var receivedOrderId: Int? = null
        var receivedFaceImageBase64: String? = null

        override suspend fun checkFace(
            orderId: Int,
            faceImageBase64: String,
        ): CheckFaceRemoteResult {
            callCount += 1
            receivedOrderId = orderId
            receivedFaceImageBase64 = faceImageBase64
            return result
        }
    }
}
