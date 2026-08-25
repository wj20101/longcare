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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Converts face images for the current request and removes locally persisted face artifacts.
 *
 * The registered face on the server is authoritative and is never mirrored into a new local cache.
 * [clearUserFaceArtifacts] removes files and DataStore records left by the current capture flow and
 * older app versions when the server reports no registered face.
 */
@Singleton
class IdentificationFaceDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FaceCacheCleaner {
    private val userDataStoreCache = ConcurrentHashMap<Int, DataStore<Preferences>>()

    private fun getDataStoreForUser(userId: Int): DataStore<Preferences> =
        userDataStoreCache.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }

    private fun faceCacheRecordKey(userId: Int) =
        stringPreferencesKey(FACE_CACHE_RECORD_KEY_PREFIX + userId)

    override suspend fun clearUserFaceArtifacts(userId: Int) {
        try {
            getDataStoreForUser(userId).edit { preferences ->
                preferences.remove(faceCacheRecordKey(userId))
            }
            withContext(ioDispatcher) {
                deleteLegacyUserFaceFiles(userId)
                deleteCapturedFaceDirectories()
                deleteObsoleteFaceTempDirectory()
            }
            logD("清除本地人脸数据 (userId=$userId)", tag = TAG)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logE("清除本地人脸数据异常 (userId=$userId)", tag = TAG, throwable = error)
        }
    }

    suspend fun imageFileToBase64(imageFile: File): String = withContext(ioDispatcher) {
        encodeFileToBase64(imageFile)
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

    private fun deleteObsoleteFaceTempDirectory() {
        val directory = File(context.cacheDir, OBSOLETE_FACE_TEMP_DIR_NAME)
        if (directory.exists() && !directory.deleteRecursively()) {
            logE("删除旧版人脸临时目录失败: ${directory.absolutePath}", tag = TAG)
        }
    }

    private companion object {
        const val TAG = "IdentificationFaceDataSource"
        const val FACE_CACHE_RECORD_KEY_PREFIX = "face_cache_record_user_"
        const val LEGACY_FACE_STORE_DIR_NAME = "face_store"
        const val OBSOLETE_FACE_TEMP_DIR_NAME = "face_temp"
        val CAPTURED_FACE_DIR_NAMES = listOf("face_captures", "face_capture")
    }
}
