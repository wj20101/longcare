package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `clearUserFaceBase64 removes cached base64 and local captured face files`() = runTest(testDispatcher) {
        val userId = 13579
        val cachedFace = base64Of("cached-face-bytes")
        val currentFaceFile = File(context.filesDir, "face_captures/test_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("current")
        }
        val legacyFaceFile = File(context.filesDir, "face_capture/legacy_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("legacy")
        }

        dataSource.writeUserFaceBase64(userId, cachedFace)

        assertTrue(currentFaceFile.exists())
        assertTrue(legacyFaceFile.exists())

        dataSource.clearUserFaceBase64(userId)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertFalse(currentFaceFile.exists())
        assertFalse(legacyFaceFile.exists())
    }

    @Test
    fun `writeUserFaceBase64 stores metadata instead of plaintext base64`() = runTest(testDispatcher) {
        val userId = 24680
        val cachedFace = base64Of("face-cache-content")

        dataSource.writeUserFaceBase64(userId, cachedFace)

        assertEquals(cachedFace, dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().any { file ->
            file.isFile && file.name.startsWith("face_user_${userId}_")
        })
        assertFalse(userDataStoreFile(userId).readBytes().decodeToString().contains(cachedFace))
    }

    @Test
    fun `readUserFaceBase64 migrates legacy plaintext base64 key to file backed cache`() = runTest(testDispatcher) {
        val userId = 11223
        val legacyBase64 = base64Of("legacy-face-content")
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_base64_user_$userId")] = legacyBase64
        }

        assertEquals(legacyBase64, dataSource.readUserFaceBase64(userId))

        val dataStoreText = userDataStoreFile(userId).readBytes().decodeToString()
        assertFalse(dataStoreText.contains(legacyBase64))
        assertFalse(dataStoreText.contains("face_base64_user_$userId"))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().any { file ->
            file.isFile && file.name.startsWith("face_user_${userId}_")
        })
    }

    private fun deleteFaceArtifacts() {
        File(context.filesDir, "face_captures").deleteRecursively()
        File(context.filesDir, "face_capture").deleteRecursively()
        File(context.filesDir, "face_store").deleteRecursively()
    }

    @Suppress("UNCHECKED_CAST")
    private fun dataStoreForUser(userId: Int): DataStore<Preferences> {
        val method = IdentificationFaceDataSource::class.java.getDeclaredMethod(
            "getDataStoreForUser",
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(dataSource, userId) as DataStore<Preferences>
    }

    private fun userDataStoreFile(userId: Int): File {
        return File(context.filesDir, "datastore/user_${userId}_prefs.preferences_pb")
    }

    private fun base64Of(value: String): String {
        return Base64.encodeToString(value.toByteArray(), Base64.NO_WRAP)
    }
}
