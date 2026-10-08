package com.ytone.longcare.features.identification.domain

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SetupFaceUseCaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `server success returns success and refreshes session`() = runBlocking {
        val gateway = FakeSetupFaceGateway()
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
            ensureCurrentSession = {},
        )

        assertTrue(result is SetupFaceResult.Success)
        assertEquals(
            listOf("uploadFaceImage", "setFaceOnServer", "refreshCurrentUserSession"),
            gateway.callOrder,
        )
        assertTrue(gateway.sessionRefreshed)
    }

    @Test
    fun `upload failure returns error without server update or refresh`() = runBlocking {
        val gateway = FakeSetupFaceGateway(
            uploadResult = SetupFaceUploadResult.Error("图片上传失败"),
        )
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
            ensureCurrentSession = {},
        )

        assertTrue(result is SetupFaceResult.Error)
        result as SetupFaceResult.Error
        assertEquals(SetupFaceFailure.ImageUpload("图片上传失败"), result.failure)
        assertEquals(listOf("uploadFaceImage"), gateway.callOrder)
        assertFalse(gateway.sessionRefreshed)
    }

    @Test
    fun `server failure returns error without refresh`() = runBlocking {
        val gateway = FakeSetupFaceGateway(
            serverResult = SetupFaceServerResult.Rejected("服务器更新失败"),
        )
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
            ensureCurrentSession = {},
        )

        assertTrue(result is SetupFaceResult.Error)
        result as SetupFaceResult.Error
        assertEquals(SetupFaceFailure.ServerRejected("服务器更新失败"), result.failure)
        assertEquals(listOf("uploadFaceImage", "setFaceOnServer"), gateway.callOrder)
        assertFalse(gateway.sessionRefreshed)
    }

    @Test
    fun `session change during upload prevents face registration and success`() = kotlinx.coroutines.test.runTest {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finish = kotlinx.coroutines.CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        var currentSession = true
        val gateway = object : SetupFaceGateway {
            override suspend fun uploadFaceImage(imageFile: File): SetupFaceUploadResult {
                calls += "upload"
                started.complete(Unit)
                finish.await()
                return SetupFaceUploadResult.Success("face-key")
            }
            override suspend fun setFaceOnServer(base64Image: String, uploadedKey: String): SetupFaceServerResult {
                calls += "setFace"
                return SetupFaceServerResult.Success
            }
            override suspend fun refreshCurrentUserSession() { calls += "refresh" }
        }
        val useCase = SetupFaceUseCase(gateway)
        var deliveredSuccess = false
        val pending = async {
            useCase.execute(temporaryFolder.newFile("face.jpg"), "photo", 123) {
                if (!currentSession) throw kotlinx.coroutines.CancellationException("Session changed")
            }
            deliveredSuccess = true
        }
        started.await()
        currentSession = false
        finish.complete(Unit)
        org.junit.Assert.assertTrue(
            "Stale upload must be cancelled",
            runCatching { pending.await() }.exceptionOrNull() is kotlinx.coroutines.CancellationException,
        )
        assertEquals(listOf("upload"), calls)
        assertFalse(deliveredSuccess)
    }

    private class FakeSetupFaceGateway(
        private val uploadResult: SetupFaceUploadResult = SetupFaceUploadResult.Success(
            uploadedKey = "face-key",
        ),
        private val serverResult: SetupFaceServerResult = SetupFaceServerResult.Success,
    ) : SetupFaceGateway {
        val callOrder = mutableListOf<String>()
        var sessionRefreshed = false

        override suspend fun uploadFaceImage(imageFile: File): SetupFaceUploadResult {
            callOrder.add("uploadFaceImage")
            return uploadResult
        }

        override suspend fun setFaceOnServer(
            base64Image: String,
            uploadedKey: String,
        ): SetupFaceServerResult {
            callOrder.add("setFaceOnServer")
            return serverResult
        }

        override suspend fun refreshCurrentUserSession() {
            callOrder.add("refreshCurrentUserSession")
            sessionRefreshed = true
        }
    }
}
