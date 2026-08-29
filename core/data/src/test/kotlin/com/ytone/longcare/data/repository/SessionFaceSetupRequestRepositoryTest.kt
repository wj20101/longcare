package com.ytone.longcare.data.repository

import com.ytone.longcare.data.session.FaceSetupIdentitySecret
import com.ytone.longcare.data.session.RequestAuthSnapshot
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.domain.faceauth.FaceSetupRequestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFaceSetupRequestRepositoryTest {

    @Test
    fun `creates purpose limited request without exposing session payload`() {
        val provider = FakeSecretProvider(
            FaceSetupIdentitySecret(
                userId = 7,
                userName = "张三",
                identityCardNumber = "110101199001010011",
            )
        )

        val result = SessionFaceSetupRequestRepository(provider)
            .createFaceSetupRequest(orderNo = "order-1", sourcePhotoBase64 = "photo")

        assertTrue(result is FaceSetupRequestResult.Ready)
        val request = (result as FaceSetupRequestResult.Ready).request
        assertEquals("张三", request.name)
        assertEquals("110101199001010011", request.idNo)
        assertEquals("7", request.userId)
        assertEquals("order-1", request.orderNo)
        assertEquals("photo", request.sourcePhotoStr)
    }

    @Test
    fun `missing active session fails closed`() {
        val result = SessionFaceSetupRequestRepository(FakeSecretProvider(null))
            .createFaceSetupRequest(orderNo = "order-1", sourcePhotoBase64 = "photo")

        assertEquals(FaceSetupRequestResult.SessionUnavailable, result)
    }

    @Test
    fun `incomplete real name identity fails closed`() {
        val result = SessionFaceSetupRequestRepository(
            FakeSecretProvider(
                FaceSetupIdentitySecret(userId = 7, userName = "", identityCardNumber = "id")
            )
        ).createFaceSetupRequest(orderNo = "order-1", sourcePhotoBase64 = "photo")

        assertEquals(FaceSetupRequestResult.IdentityIncomplete, result)
    }

    private class FakeSecretProvider(
        private val identity: FaceSetupIdentitySecret?,
    ) : SessionSecretProvider {
        override fun requestAuthSnapshot(): RequestAuthSnapshot? = null
        override fun faceSetupIdentity(): FaceSetupIdentitySecret? = identity
        override fun activeSessionFingerprint(): String? = null
    }
}
