package com.ytone.longcare.features.identification.data

import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.data.storage.DataStoreKeys
import com.ytone.longcare.data.storage.UserSpecificDataStoreManager
import com.ytone.longcare.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class IdentificationFaceDataSource @Inject constructor(
    private val userSpecificDataStoreManager: UserSpecificDataStoreManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "IdentificationFaceDataSource"
    }

    suspend fun readUserFaceBase64(userId: Int): String? {
        return try {
            val dataStore = userSpecificDataStoreManager.getDataStoreForUser(userId)
            val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
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
            val dataStore = userSpecificDataStoreManager.getDataStoreForUser(userId)
            val key = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
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
}
