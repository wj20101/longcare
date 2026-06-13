package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
import android.util.Base64InputStream
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Singleton
class IdentificationFaceDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FaceCacheCleaner {
    companion object {
        private const val TAG = "IdentificationFaceDataSource"
        private const val FACE_CACHE_RECORD_KEY_PREFIX = "face_cache_record_user_"
        private const val FACE_TEMP_DIR_NAME = "face_temp"
        private const val REMOTE_FACE_DOWNLOAD_FILE_PREFIX = "remote_face_"
        private const val REMOTE_FACE_DOWNLOAD_FILE_SUFFIX = ".img"
        private const val LOCAL_FACE_DECODE_FILE_PREFIX = "local_face_"
        private const val LOCAL_FACE_DECODE_FILE_SUFFIX = ".img"
        private val CAPTURED_FACE_DIR_NAMES = listOf("face_captures", "face_capture")
    }

    private val userDataStoreCache = ConcurrentHashMap<Int, DataStore<Preferences>>()
    private val faceFileStore = FaceFileStore(context, ioDispatcher)
    private val remoteFaceImageDownloader = RemoteFaceImageDownloader()

    private fun getDataStoreForUser(userId: Int): DataStore<Preferences> {
        return userDataStoreCache.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }
    }

    private fun faceCacheRecordKey(userId: Int) =
        stringPreferencesKey(FACE_CACHE_RECORD_KEY_PREFIX + userId)

    suspend fun readUserFaceBase64(userId: Int): String? {
        return try {
            val dataStore = getDataStoreForUser(userId)
            val preferences = dataStore.data.first()
            val recordKey = faceCacheRecordKey(userId)

            val cachedRecordValue = preferences[recordKey]
            val cachedRecord = cachedRecordValue?.toFaceCacheRecord()
            if (cachedRecord != null) {
                readRecordAsBase64OrNull(
                    userId = userId,
                    record = cachedRecord,
                    dataStore = dataStore,
                    recordKey = recordKey,
                )?.let { return it }
            } else if (cachedRecordValue != null) {
                dataStore.edit { prefs ->
                    prefs.remove(recordKey)
                }
                faceFileStore.deleteUserFaceFiles(userId)
                logE("人脸缓存记录格式无效，已移除 (userId=$userId)", tag = TAG)
            }

            logD("人脸缓存为空 (userId=$userId)", tag = TAG)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("读取人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
            null
        }
    }

    suspend fun writeUserFaceBase64(userId: Int, base64: String): Boolean {
        return try {
            withContext(ioDispatcher) {
                val decodedFile = createLocalFaceDecodeFile()
                try {
                    writeBase64ToFile(base64, decodedFile)
                    persistUserFaceFile(userId, decodedFile)
                } finally {
                    decodedFile.delete()
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("写入人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
            false
        }
    }

    override suspend fun clearUserFaceBase64(userId: Int) {
        try {
            val dataStore = getDataStoreForUser(userId)
            val recordKey = faceCacheRecordKey(userId)
            dataStore.edit { prefs ->
                prefs.remove(recordKey)
            }
            faceFileStore.deleteUserFaceFiles(userId)
            clearLocalFaceFiles()
            logD("清除人脸缓存 (userId=$userId)", tag = TAG)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("清除人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
        }
    }

    suspend fun imageFileToBase64(imageFile: File): String {
        return withContext(ioDispatcher) {
            encodeFileToBase64(imageFile)
        }
    }

    suspend fun downloadCacheAndConvertToBase64(url: String, userId: Int): String {
        return withContext(ioDispatcher) {
            val downloadedFile = createRemoteFaceDownloadFile()
            try {
                remoteFaceImageDownloader.downloadToFile(url, downloadedFile)
                persistUserFaceFile(userId, downloadedFile)
                encodeFileToBase64(downloadedFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("处理远程人脸图片失败", tag = TAG, throwable = e)
                throw e
            } finally {
                downloadedFile.delete()
            }
        }
    }

    private suspend fun readRecordAsBase64(
        userId: Int,
        record: FaceCacheRecord,
    ): String {
        val file = faceFileStore.readFaceFile(record)
        val base64 = encodeFileToBase64(file)
        logD("成功读取人脸文件缓存 (userId=$userId, 长度=${base64.length})", tag = TAG)
        return base64
    }

    private suspend fun readRecordAsBase64OrNull(
        userId: Int,
        record: FaceCacheRecord,
        dataStore: DataStore<Preferences>,
        recordKey: Preferences.Key<String>,
    ): String? {
        return try {
            readRecordAsBase64(userId, record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("人脸文件缓存无效，已忽略 (userId=$userId)", tag = TAG, throwable = e)
            dataStore.edit { prefs ->
                prefs.remove(recordKey)
            }
            faceFileStore.deleteUserFaceFiles(userId)
            null
        }
    }

    private suspend fun persistUserFaceFile(userId: Int, imageFile: File) {
        persistFaceCacheRecord(
            userId = userId,
            record = faceFileStore.writeFaceFile(userId, imageFile),
        )
    }

    private suspend fun persistFaceCacheRecord(
        userId: Int,
        record: FaceCacheRecord,
    ) {
        val dataStore = getDataStoreForUser(userId)
        val recordKey = faceCacheRecordKey(userId)
        dataStore.edit { prefs ->
            prefs[recordKey] = record.toPreferenceValue()
        }
        logD("成功写入人脸文件缓存 (userId=$userId, size=${record.sizeBytes})", tag = TAG)
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
        return createTempFaceFile(
            prefix = REMOTE_FACE_DOWNLOAD_FILE_PREFIX,
            suffix = REMOTE_FACE_DOWNLOAD_FILE_SUFFIX,
        )
    }

    private fun createLocalFaceDecodeFile(): File {
        return createTempFaceFile(
            prefix = LOCAL_FACE_DECODE_FILE_PREFIX,
            suffix = LOCAL_FACE_DECODE_FILE_SUFFIX,
        )
    }

    private fun createTempFaceFile(prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, FACE_TEMP_DIR_NAME).apply {
            if (!exists() && !mkdirs() && !exists()) {
                throw IOException("Failed to create face temp directory.")
            }
        }
        return File(dir, "$prefix${UUID.randomUUID()}$suffix")
    }

    private fun writeBase64ToFile(base64: String, destinationFile: File) {
        base64.byteInputStream(Charsets.US_ASCII).use { rawInput ->
            Base64InputStream(rawInput, Base64.NO_WRAP).use { base64Input ->
                destinationFile.outputStream().use { output ->
                    base64Input.copyTo(output)
                }
            }
        }
    }

    private suspend fun clearLocalFaceFiles() = withContext(ioDispatcher) {
        CAPTURED_FACE_DIR_NAMES.forEach { dirName ->
            val dir = File(context.filesDir, dirName)
            if (dir.exists() && !dir.deleteRecursively()) {
                logE("删除本地人脸文件目录失败: ${dir.absolutePath}", tag = TAG)
            }
        }
    }
}

private fun FaceCacheRecord.toPreferenceValue(): String {
    return listOf(fileName, sha256, createdAtMillis, sizeBytes).joinToString(separator = "|")
}

private fun String.toFaceCacheRecord(): FaceCacheRecord? {
    val parts = split("|")
    if (parts.size != 4) return null
    return FaceCacheRecord(
        fileName = parts[0],
        sha256 = parts[1],
        createdAtMillis = parts[2].toLongOrNull() ?: return null,
        sizeBytes = parts[3].toLongOrNull() ?: return null,
    )
}
