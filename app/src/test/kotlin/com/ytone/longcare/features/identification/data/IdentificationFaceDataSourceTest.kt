package com.ytone.longcare.features.identification.data

import com.ytone.longcare.domain.facecache.UserFaceArtifactStorage
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class IdentificationFaceDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `cleanup delegates only to current user storage boundary`() = runTest(testDispatcher) {
        val storage = RecordingFaceArtifactStorage()
        val dataSource = IdentificationFaceDataSource(storage, testDispatcher)

        dataSource.clearUserFaceArtifacts(13_579)

        assertEquals(listOf(13_579), storage.clearedUserIds)
    }

    @Test
    fun `image conversion is request scoped`() = runTest(testDispatcher) {
        val imageFile = temporaryFolder.newFile("face-input.img").apply {
            writeText("face-content")
        }
        val dataSource = IdentificationFaceDataSource(RecordingFaceArtifactStorage(), testDispatcher)

        val encoded = dataSource.imageFileToBase64(imageFile)

        assertEquals(
            Base64.getEncoder().encodeToString("face-content".toByteArray()),
            encoded,
        )
    }

    private class RecordingFaceArtifactStorage : UserFaceArtifactStorage {
        val clearedUserIds = mutableListOf<Int>()

        override suspend fun clearCurrentFaceArtifacts(expectedUserId: Int) {
            clearedUserIds += expectedUserId
        }
    }
}
