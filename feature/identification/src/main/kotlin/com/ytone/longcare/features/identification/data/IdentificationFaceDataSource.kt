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
        private const val FACE_BASE64_KEY_PREFIX = "face_base64_user_"
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

    private fun faceBase64Key(userId: Int) = stringPreferencesKey(FACE_BASE64_KEY_PREFIX + userId)

    private fun faceCacheRecordKey(userId: Int) =
        stringPreferencesKey(FACE_CACHE_RECORD_KEY_PREFIX + userId)

    suspend fun readUserFaceBase64(userId: Int): String? {
        return try {
            val dataStore = getDataStoreForUser(userId)
            val preferences = dataStore.data.first()
            val recordKey = faceCacheRecordKey(userId)
            val legacyKey = faceBase64Key(userId)

            val cachedRecord = preferences[recordKey]?.toFaceCacheRecord()
            if (cachedRecord != null) {
                return readRecordAsBase64(userId, cachedRecord)
            }

            val legacyBase64 = preferences[legacyKey]
            if (!legacyBase64.isNullOrBlank()) {
                val bytes = Base64.decode(legacyBase64, Base64.NO_WRAP)
                val record = faceFileStore.writeFaceBytes(userId, bytes)
                dataStore.edit { prefs ->
                    prefs[recordKey] = record.toPreferenceValue()
                    prefs.remove(legacyKey)
                }
                logD("已迁移旧版人脸缓存 (userId=$userId, 长度=${legacyBase64.length})", tag = TAG)
                return legacyBase64
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
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val record = faceFileStore.writeFaceBytes(userId, bytes)
            val dataStore = getDataStoreForUser(userId)
            val recordKey = faceCacheRecordKey(userId)
            val legacyKey = faceBase64Key(userId)
            dataStore.edit { prefs ->
                prefs[recordKey] = record.toPreferenceValue()
                prefs.remove(legacyKey)
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
            val legacyKey = faceBase64Key(userId)
            dataStore.edit { prefs ->
                prefs.remove(recordKey)
                prefs.remove(legacyKey)
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
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        logD("成功读取人脸文件缓存 (userId=$userId, 长度=${base64.length})", tag = TAG)
        return base64
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
