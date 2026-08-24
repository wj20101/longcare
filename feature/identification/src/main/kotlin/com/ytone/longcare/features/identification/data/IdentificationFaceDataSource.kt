package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
import android.util.Base64OutputStream
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker
import com.ytone.longcare.features.identification.tracker.FaceVerificationEventTracker.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Converts face images for the current request and removes legacy persistent face artifacts.
 *
 * Face verification always resolves the current server source, so newly processed face images are
 * intentionally not persisted. [clearUserFaceBase64] remains for privacy-safe cleanup of files and
 * DataStore records created by older app versions.
 */
@Singleton
class IdentificationFaceDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FaceCacheCleaner {
    private val userDataStoreCache = ConcurrentHashMap<Int, DataStore<Preferences>>()
    private val remoteFaceImageDownloader = RemoteFaceImageDownloader()

    private fun getDataStoreForUser(userId: Int): DataStore<Preferences> =
        userDataStoreCache.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }

    private fun faceCacheRecordKey(userId: Int) =
        stringPreferencesKey(FACE_CACHE_RECORD_KEY_PREFIX + userId)

    override suspend fun clearUserFaceBase64(userId: Int) {
        try {
            getDataStoreForUser(userId).edit { preferences ->
                preferences.remove(faceCacheRecordKey(userId))
            }
            withContext(ioDispatcher) {
                deleteLegacyUserFaceFiles(userId)
                deleteCapturedFaceDirectories()
            }
            logD("清除旧版人脸缓存 (userId=$userId)", tag = TAG)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logE("清除旧版人脸缓存异常 (userId=$userId)", tag = TAG, throwable = error)
        }
    }

    suspend fun imageFileToBase64(imageFile: File): String = withContext(ioDispatcher) {
        encodeFileToBase64(imageFile)
    }

    suspend fun downloadAndConvertToBase64(
        url: String,
        userId: Int,
    ): String = withContext(ioDispatcher) {
        val downloadedFile = createRemoteFaceDownloadFile()
        try {
            remoteFaceImageDownloader.downloadToFile(url, downloadedFile)
            val downloadedSizeBytes = downloadedFile.length()
            val base64 = encodeFileToBase64(downloadedFile)
            FaceVerificationEventTracker.trackEvent(
                eventType = EventType.REMOTE_FACE_DOWNLOAD_SUCCESS,
                extras = FaceVerificationEventTracker.safeUrlExtras(url) + mapOf(
                    "userId" to userId,
                    "sizeBytes" to downloadedSizeBytes,
                    "sourcePhotoBase64Length" to base64.length,
                ),
            )
            base64
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logE("处理远程人脸图片失败", tag = TAG, throwable = error)
            throw error
        } finally {
            if (downloadedFile.exists() && !downloadedFile.delete()) {
                logE("删除临时人脸图片失败: ${downloadedFile.absolutePath}", tag = TAG)
            }
        }
    }

    private fun encodeFileToBase64(imageFile: File): String {
        val output = ByteArrayOutputStream()
        Base64OutputStream(output, Base64.NO_WRAP).use { base64Output ->
            imageFile.inputStream().use { input ->
                input.copyTo(base64Output)
            }
        }
        return output.toString(Charsets.US_ASCII.name())
    }

    private fun createRemoteFaceDownloadFile(): File {
        val directory = File(context.cacheDir, FACE_TEMP_DIR_NAME).apply {
            if (!exists() && !mkdirs() && !exists()) {
                throw IOException("Failed to create face temp directory.")
            }
        }
        return File(
            directory,
            "$REMOTE_FACE_DOWNLOAD_FILE_PREFIX${UUID.randomUUID()}$REMOTE_FACE_DOWNLOAD_FILE_SUFFIX",
        )
    }

    private fun deleteLegacyUserFaceFiles(userId: Int) {
        val directory = File(context.filesDir, LEGACY_FACE_STORE_DIR_NAME)
        val filePrefix = "face_user_${userId}_"
        directory.listFiles { file -> file.isFile && file.name.startsWith(filePrefix) }
            .orEmpty()
            .forEach { file ->
                if (!file.delete()) {
                    logE("删除旧版人脸缓存文件失败: ${file.absolutePath}", tag = TAG)
                }
            }
        if (directory.isDirectory && directory.listFiles().isNullOrEmpty()) {
            directory.delete()
        }
    }

    private fun deleteCapturedFaceDirectories() {
        CAPTURED_FACE_DIR_NAMES.forEach { directoryName ->
            val directory = File(context.filesDir, directoryName)
            if (directory.exists() && !directory.deleteRecursively()) {
                logE("删除本地人脸文件目录失败: ${directory.absolutePath}", tag = TAG)
            }
        }
    }

    private companion object {
        const val TAG = "IdentificationFaceDataSource"
        const val FACE_CACHE_RECORD_KEY_PREFIX = "face_cache_record_user_"
        const val FACE_TEMP_DIR_NAME = "face_temp"
        const val REMOTE_FACE_DOWNLOAD_FILE_PREFIX = "remote_face_"
        const val REMOTE_FACE_DOWNLOAD_FILE_SUFFIX = ".img"
        const val LEGACY_FACE_STORE_DIR_NAME = "face_store"
        val CAPTURED_FACE_DIR_NAMES = listOf("face_captures", "face_capture")
    }
}
