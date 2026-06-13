package com.ytone.longcare.features.identification.domain

import java.io.File
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
    fun `server success and cache success returns success and refreshes session`() = runBlocking {
        val gateway = FakeSetupFaceGateway(cacheResult = true)
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
        )

        assertTrue(result is SetupFaceResult.Success)
        assertEquals(listOf("uploadFaceImage", "setFaceOnServer", "cacheUserFace", "refreshCurrentUserSession"), gateway.callOrder)
        assertTrue(gateway.sessionRefreshed)
    }

    @Test
    fun `server success and cache failure returns error without refreshing session`() = runBlocking {
        val gateway = FakeSetupFaceGateway(cacheResult = false)
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
        )

        assertTrue(result is SetupFaceResult.Error)
        result as SetupFaceResult.Error
        assertEquals("本地人脸缓存失败，请重试", result.message)
        assertEquals(listOf("uploadFaceImage", "setFaceOnServer", "cacheUserFace"), gateway.callOrder)
        assertFalse(gateway.sessionRefreshed)
    }

    @Test
    fun `upload failure returns error without server update cache or refresh`() = runBlocking {
        val gateway = FakeSetupFaceGateway(
            uploadResult = SetupFaceUploadResult.Error("图片上传失败"),
            cacheResult = true,
        )
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
        )

        assertTrue(result is SetupFaceResult.Error)
        result as SetupFaceResult.Error
        assertEquals("图片上传失败", result.message)
        assertEquals(listOf("uploadFaceImage"), gateway.callOrder)
        assertFalse(gateway.sessionRefreshed)
    }

    @Test
    fun `server failure returns error without cache or refresh`() = runBlocking {
        val gateway = FakeSetupFaceGateway(
            serverResult = SetupFaceServerResult.Error("服务器更新失败"),
            cacheResult = true,
        )
        val useCase = SetupFaceUseCase(gateway)

        val result = useCase.execute(
            imageFile = temporaryFolder.newFile("face.jpg"),
            base64Image = "face-base64",
            currentUserId = 123,
        )

        assertTrue(result is SetupFaceResult.Error)
        result as SetupFaceResult.Error
        assertEquals("服务器更新失败", result.message)
        assertEquals(listOf("uploadFaceImage", "setFaceOnServer"), gateway.callOrder)
        assertFalse(gateway.sessionRefreshed)
    }

    private class FakeSetupFaceGateway(
        private val uploadResult: SetupFaceUploadResult = SetupFaceUploadResult.Success(uploadedKey = "face-key"),
        private val serverResult: SetupFaceServerResult = SetupFaceServerResult.Success,
        private val cacheResult: Boolean,
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

        override suspend fun cacheUserFace(
            userId: Int,
            base64Image: String,
        ): Boolean {
            callOrder.add("cacheUserFace")
            return cacheResult
        }

        override fun refreshCurrentUserSession() {
            callOrder.add("refreshCurrentUserSession")
            sessionRefreshed = true
        }
    }
}
