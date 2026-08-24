package com.ytone.longcare.features.identification.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `legacy cleanup removes only current user cache plus captured face files`() =
        runTest(testDispatcher) {
            val userId = 13_579
            val otherUserId = 24_680
            val recordKey = stringPreferencesKey("face_cache_record_user_$userId")
            val dataStore = dataStoreForUser(userId)
            dataStore.edit { preferences -> preferences[recordKey] = "legacy-record" }

            val currentUserFile = legacyFaceFile(userId).apply { writeText("current") }
            val otherUserFile = legacyFaceFile(otherUserId).apply { writeText("other") }
            val currentFaceFile = File(
                context.filesDir,
                "face_captures/test_face.jpg",
            ).apply {
                parentFile?.mkdirs()
                writeText("current")
            }
            val appFaceFile = File(
                context.filesDir,
                "face_capture/app_face.jpg",
            ).apply {
                parentFile?.mkdirs()
                writeText("app")
            }

            dataSource.clearUserFaceBase64(userId)

            assertNull(dataStore.data.first()[recordKey])
            assertFalse(currentUserFile.exists())
            assertTrue(otherUserFile.exists())
            assertFalse(currentFaceFile.exists())
            assertFalse(appFaceFile.exists())
        }

    @Test
    fun `image conversion is request scoped and leaves no persistent face copy`() =
        runTest(testDispatcher) {
            val imageFile = File(context.cacheDir, "face-input.img").apply {
                writeText("face-content")
            }

            val encoded = dataSource.imageFileToBase64(imageFile)

            assertEquals(
                Base64.getEncoder().encodeToString("face-content".toByteArray()),
                encoded,
            )
            assertFalse(File(context.filesDir, "face_store").exists())
        }

    private fun legacyFaceFile(userId: Int): File {
        val directory = File(context.filesDir, "face_store").apply { mkdirs() }
        return File(directory, "face_user_${userId}_${"A".repeat(64)}.img")
    }

    private fun deleteFaceArtifacts() {
        File(context.filesDir, "face_captures").deleteRecursively()
        File(context.filesDir, "face_capture").deleteRecursively()
        File(context.filesDir, "face_store").deleteRecursively()
        File(context.cacheDir, "face-input.img").delete()
    }

    @Suppress("UNCHECKED_CAST")
    private fun dataStoreForUser(userId: Int): DataStore<Preferences> {
        val method = IdentificationFaceDataSource::class.java.getDeclaredMethod(
            "getDataStoreForUser",
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(dataSource, userId) as DataStore<Preferences>
    }
}
