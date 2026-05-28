package com.ytone.longcare.features.identification.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

@Singleton
class IdentificationFaceDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FaceCacheCleaner {
    companion object {
        private const val TAG = "IdentificationFaceDataSource"
        private const val FACE_BASE64_KEY_PREFIX = "face_base64_user_"
        private val CAPTURED_FACE_DIR_NAMES = listOf("face_captures", "face_capture")
    }

    private val userDataStoreCache = ConcurrentHashMap<Int, DataStore<Preferences>>()

    private fun getDataStoreForUser(userId: Int): DataStore<Preferences> {
        return userDataStoreCache.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }
    }

    private fun faceBase64Key(userId: Int) = stringPreferencesKey(FACE_BASE64_KEY_PREFIX + userId)

    suspend fun readUserFaceBase64(userId: Int): String? {
        return try {
            val dataStore = getDataStoreForUser(userId)
            val key = faceBase64Key(userId)
            val result = dataStore.data.first()[key]
            if (result != null) {
                logD("成功读取人脸缓存 (userId=$userId, 长度=${result.length})", tag = TAG)
            } else {
                logD("人脸缓存为空 (userId=$userId)", tag = TAG)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("读取人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
            null
        }
    }

    suspend fun writeUserFaceBase64(userId: Int, base64: String) {
        try {
            val dataStore = getDataStoreForUser(userId)
            val key = faceBase64Key(userId)
            dataStore.edit { prefs ->
                prefs[key] = base64
            }
            logD("成功写入人脸缓存 (userId=$userId, 长度=${base64.length})", tag = TAG)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("写入人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
        }
    }

    override suspend fun clearUserFaceBase64(userId: Int) {
        try {
            val dataStore = getDataStoreForUser(userId)
            val key = faceBase64Key(userId)
            dataStore.edit { prefs ->
                prefs.remove(key)
            }
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
                val bytes = URL(url).readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("下载图片失败: $url", tag = TAG, throwable = e)
                throw e
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
