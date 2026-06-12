package com.ytone.longcare.features.identification.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
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
        val cachedFace = base64OfPng()

        dataSource.writeUserFaceBase64(userId, cachedFace)

        assertEquals(cachedFace, dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().any { file ->
            file.isFile && file.name.startsWith("face_user_${userId}_")
        })
        assertFalse(userDataStoreFile(userId).readBytes().decodeToString().contains(cachedFace))
    }

    @Test
    fun `readUserFaceBase64 ignores legacy plaintext base64 key`() = runTest(testDispatcher) {
        val userId = 11223
        val legacyBase64 = base64OfPng()
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_base64_user_$userId")] = legacyBase64
        }

        assertNull(dataSource.readUserFaceBase64(userId))

        val dataStoreText = userDataStoreFile(userId).readBytes().decodeToString()
        assertTrue(dataStoreText.contains(legacyBase64))
        assertTrue(dataStoreText.contains("face_base64_user_$userId"))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `writeUserFaceBase64 ignores invalid image base64`() = runTest(testDispatcher) {
        val userId = 33445
        val invalidFace = base64Of("not-image-content")

        dataSource.writeUserFaceBase64(userId, invalidFace)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `writeUserFaceBase64 ignores undecodable image base64`() = runTest(testDispatcher) {
        val userId = 44556
        val invalidFace = Base64.encodeToString(truncatedJpegHeaderBytes(), Base64.NO_WRAP)

        dataSource.writeUserFaceBase64(userId, invalidFace)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `writeUserFaceBase64 ignores corrupted image with valid header`() = runTest(testDispatcher) {
        val userId = 45678
        val invalidFace = Base64.encodeToString(corruptedJpegWithReadableBoundsBytes(), Base64.NO_WRAP)

        dataSource.writeUserFaceBase64(userId, invalidFace)

        assertNull(dataSource.readUserFaceBase64(userId))
        assertTrue(File(context.filesDir, "face_store").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `readUserFaceBase64 treats invalid file cache as missing`() = runTest(testDispatcher) {
        val userId = 55667
        val invalidBytes = "not-image-content".toByteArray()
        val sha256 = invalidBytes.sha256Hex()
        val fileName = "face_user_${userId}_$sha256.img"
        File(context.filesDir, "face_store").apply { mkdirs() }
        File(context.filesDir, "face_store/$fileName").writeBytes(invalidBytes)
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_cache_record_user_$userId")] =
                "$fileName|$sha256|1|${invalidBytes.size}"
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

    @Test
    fun `readUserFaceBase64 leaves invalid legacy plaintext base64 untouched`() = runTest(testDispatcher) {
        val userId = 77889
        val invalidLegacyBase64 = base64Of("not-image-content")
        val dataStore = dataStoreForUser(userId)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("face_base64_user_$userId")] = invalidLegacyBase64
        }

        assertNull(dataSource.readUserFaceBase64(userId))

        val dataStoreText = userDataStoreFile(userId).readBytes().decodeToString()
        assertTrue(dataStoreText.contains(invalidLegacyBase64))
        assertTrue(dataStoreText.contains("face_base64_user_$userId"))
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

    private fun truncatedJpegHeaderBytes(): ByteArray {
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
    }

    private fun corruptedJpegWithReadableBoundsBytes(): ByteArray {
        val validJpeg = smallJpegBytes()
        return validJpeg.copyOf((validJpeg.size / 2).coerceAtLeast(4))
    }

    private fun smallJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val output = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        bitmap.recycle()
        return output.toByteArray()
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
