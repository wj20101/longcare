package com.ytone.longcare.data.cos.repository

import android.net.Uri
import com.tencent.cos.xml.CosXmlService
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.SaveFileParamModel
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CosObjectOperationDelegateTest {

    private val apiService = mockk<LongCareApiService>()
    private val cosService = mockk<CosXmlService>(relaxed = true)
    private val config =
        CosConfig(
            region = "ap-test",
            bucket = "private-bucket",
            sessionToken = "token",
            expiredTime = Long.MAX_VALUE,
            tmpSecretId = "secret-id",
            tmpSecretKey = "secret-key",
            startTime = 0L,
            expiration = "",
            fileKeyPre = "private/service/",
        )

    @Test
    fun `upload returns key without requesting private file url`() =
        runTest {
            val delegate = createDelegate()
            val result =
                delegate.uploadFile(
                    UploadParams(
                        fileUri = Uri.fromFile(File("photo.jpg")).toString(),
                        key = "private/service/photo.jpg",
                        folderType = 13,
                    )
                )

            assertTrue(result.success)
            assertEquals("private/service/photo.jpg", result.key)
            coVerify(exactly = 0) { apiService.getFileUrl(any()) }
        }

    @Test
    fun `sales upload uses the sales service and its key prefix instead of service photos`() =
        runTest {
            val salesService = mockk<CosXmlService>(relaxed = true)
            val serviceTypes = mutableListOf<Int>()
            val configTypes = mutableListOf<Int>()
            val delegate = CosObjectOperationDelegate(
                apiService = apiService,
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                tag = "CosObjectOperationDelegateTest",
                getCosService = { folderType ->
                    serviceTypes += folderType
                    if (folderType == 15) salesService else cosService
                },
                getValidCosConfig = { folderType ->
                    configTypes += folderType
                    config.copy(fileKeyPre = if (folderType == 15) "private/sales/" else "private/service/")
                },
                clearCache = {},
            )

            val salesResult = delegate.uploadFile(
                UploadParams(Uri.fromFile(File("sales.jpg")).toString(), key = "", folderType = 15),
            )
            val serviceResult = delegate.uploadFile(
                UploadParams(Uri.fromFile(File("service.jpg")).toString(), key = "", folderType = 13),
            )

            assertTrue(salesResult.success)
            assertTrue(requireNotNull(salesResult.key).startsWith("private/sales/"))
            assertTrue(serviceResult.success)
            assertTrue(requireNotNull(serviceResult.key).startsWith("private/service/"))
            assertEquals(listOf(15, 13), serviceTypes)
            assertEquals(listOf(15, 13), configTypes)
            verify(exactly = 1) { salesService.putObject(any()) }
            verify(exactly = 1) { cosService.putObject(any()) }
            coVerify(exactly = 0) { apiService.getFileUrl(any()) }
        }

    @Test
    fun `private file url is requested only through explicit on demand call`() =
        runTest {
            val request = slot<SaveFileParamModel>()
            coEvery { apiService.getFileUrl(capture(request)) } returns
                ApiResult.Success("https://private.example/photo.jpg")
            val delegate = createDelegate()

            val url =
                delegate.getFileUrl(
                    fileKey = "private/service/photo.jpg",
                    folderType = 13,
                    fileSize = 1024L,
                )

            assertEquals("https://private.example/photo.jpg", url)
            assertEquals("private/service/photo.jpg", request.captured.fileKey)
            assertEquals(13, request.captured.folderType)
            assertEquals(1024L, request.captured.fileSize)
            coVerify(exactly = 1) { apiService.getFileUrl(any()) }
        }

    private fun kotlinx.coroutines.test.TestScope.createDelegate() =
        CosObjectOperationDelegate(
            apiService = apiService,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            tag = "CosObjectOperationDelegateTest",
            getCosService = { cosService },
            getValidCosConfig = { config },
            clearCache = {},
        )
}
