package com.ytone.longcare.features.identification.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class IdentificationFaceDataSourceTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var dataSource: IdentificationFaceDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataSource = IdentificationFaceDataSource(context, testDispatcher)
        deleteFaceArtifacts()
    }

    @After
    fun tearDown() {
        deleteFaceArtifacts()
    }

    @Test
    fun `clearUserFaceBase64 removes cached base64 and local captured face files`() = runTest(testDispatcher) {
        val userId = 13579
        val currentFaceFile = File(context.filesDir, "face_captures/test_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("current")
        }
        val legacyFaceFile = File(context.filesDir, "face_capture/legacy_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("legacy")
        }

        dataSource.writeUserFaceBase64(userId, "cached-face-base64")

        assertTrue(currentFaceFile.exists())
        assertTrue(legacyFaceFile.exists())

        dataSource.clearUserFaceBase64(userId)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertFalse(currentFaceFile.exists())
        assertFalse(legacyFaceFile.exists())
    }

    private fun deleteFaceArtifacts() {
        File(context.filesDir, "face_captures").deleteRecursively()
        File(context.filesDir, "face_capture").deleteRecursively()
    }
}
