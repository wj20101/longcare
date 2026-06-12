package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
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
import java.io.File
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

    suspend fun writeUserFaceBase64(userId: Int, base64: String) {
        try {
            val bytes = decodeFaceBase64(base64)
            val record = faceFileStore.writeFaceBytes(userId, bytes)
            val dataStore = getDataStoreForUser(userId)
            val recordKey = faceCacheRecordKey(userId)
            dataStore.edit { prefs ->
                prefs[recordKey] = record.toPreferenceValue()
            }
            logD("成功写入人脸文件缓存 (userId=$userId, size=${record.sizeBytes})", tag = TAG)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("写入人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
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
            val bytes = imageFile.readBytes()
            FaceImageValidation.requireSupportedFaceImageBytes(bytes)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    suspend fun downloadAndConvertToBase64(url: String): String {
        return withContext(ioDispatcher) {
            try {
                val bytes = remoteFaceImageDownloader.download(url)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("下载人脸图片失败", tag = TAG, throwable = e)
                throw e
            }
        }
    }

    private suspend fun readRecordAsBase64(
        userId: Int,
        record: FaceCacheRecord,
    ): String {
        val bytes = faceFileStore.readFaceBytes(record)
        FaceImageValidation.requireSupportedFaceImageBytes(bytes)
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
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

    private fun decodeFaceBase64(base64: String): ByteArray {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        FaceImageValidation.requireSupportedFaceImageBytes(bytes)
        return bytes
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
