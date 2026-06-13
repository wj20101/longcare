package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
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
        val cachedFace = base64OfPng()
        val currentFaceFile = File(context.filesDir, "face_captures/test_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("current")
        }
        val appFaceFile = File(context.filesDir, "face_capture/app_face.jpg").apply {
            parentFile?.mkdirs()
            writeText("app")
        }

        assertTrue(dataSource.writeUserFaceBase64(userId, cachedFace))

        assertTrue(currentFaceFile.exists())
        assertTrue(appFaceFile.exists())

        dataSource.clearUserFaceBase64(userId)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertFalse(currentFaceFile.exists())
        assertFalse(appFaceFile.exists())
    }

    @Test
    fun `writeUserFaceBase64 stores binary file metadata instead of plaintext base64`() = runTest(testDispatcher) {
        val userId = 33445
        val cachedFace = base64Of("not-image-content")

        assertTrue(dataSource.writeUserFaceBase64(userId, cachedFace))

        assertEquals(cachedFace, dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isNotEmpty())
        assertFalse(userDataStoreFile(userId).readBytes().decodeToString().contains(cachedFace))
    }

    @Test
    fun `writeUserFaceBase64 reuses same binary cache safely`() = runTest(testDispatcher) {
        val userId = 33556
        val cachedFace = base64Of("same-server-provided-content")

        assertTrue(dataSource.writeUserFaceBase64(userId, cachedFace))
        val firstFiles = File(context.filesDir, "face_store").listFiles().orEmpty()
            .filter { file -> file.name.startsWith("face_user_${userId}_") }
        assertEquals(1, firstFiles.size)

        assertTrue(dataSource.writeUserFaceBase64(userId, cachedFace))

        val files = File(context.filesDir, "face_store").listFiles().orEmpty()
            .filter { file -> file.name.startsWith("face_user_${userId}_") }
        assertEquals(1, files.size)
        assertEquals(firstFiles.single().name, files.single().name)
        assertEquals(cachedFace, dataSource.readUserFaceBase64(userId))
    }

    @Test
    fun `writeUserFaceBase64 ignores empty base64`() = runTest(testDispatcher) {
        val userId = 44556
        val invalidFace = ""

        assertFalse(dataSource.writeUserFaceBase64(userId, invalidFace))

        assertNull(dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `readUserFaceBase64 treats failed integrity cache as missing`() = runTest(testDispatcher) {
        val userId = 55667
        val bytes = "server-provided-content".toByteArray()
        val sha256 = bytes.sha256Hex()
        val fileName = "face_user_${userId}_$sha256.img"
        File(context.filesDir, "face_store").apply { mkdirs() }
        File(context.filesDir, "face_store/$fileName").writeBytes(bytes)
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_cache_record_user_$userId")] =
                "$fileName|$sha256|1|${bytes.size + 1}"
        }

        assertNull(dataSource.readUserFaceBase64(userId))

        val dataStoreText = userDataStoreFile(userId).readBytes().decodeToString()
        assertFalse(dataStoreText.contains("face_cache_record_user_$userId"))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `readUserFaceBase64 removes malformed cache record and orphaned user files`() = runTest(testDispatcher) {
        val userId = 66778
        val orphanBytes = pngBytes()
        val sha256 = orphanBytes.sha256Hex()
        val fileName = "face_user_${userId}_$sha256.img"
        File(context.filesDir, "face_store").apply { mkdirs() }
        File(context.filesDir, "face_store/$fileName").writeBytes(orphanBytes)
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_cache_record_user_$userId")] = "malformed-record"
        }

        assertNull(dataSource.readUserFaceBase64(userId))

        val dataStoreText = userDataStoreFile(userId).readBytes().decodeToString()
        assertFalse(dataStoreText.contains("face_cache_record_user_$userId"))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
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

    private fun base64OfPng(): String {
        return Base64.encodeToString(pngBytes(), Base64.NO_WRAP)
    }

    private fun pngBytes(): ByteArray {
        return Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4//8/AAX+Av4N70a4AAAAAElFTkSuQmCC",
            Base64.DEFAULT,
        )
    }

    private fun ByteArray.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02X".format(byte))
            }
        }
    }
}
