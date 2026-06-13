package com.ytone.longcare.features.identification.data

import android.content.Context
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal data class FaceCacheRecord(
    val fileName: String,
    val sha256: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
)

internal class FaceFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun writeFaceFile(
        userId: Int,
        sourceFile: File,
    ): FaceCacheRecord = withContext(ioDispatcher) {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            throw IOException("Face image file must not be empty.")
        }

        val sha256 = sourceFile.sha256Hex()
        val fileName = "face_user_${userId}_$sha256.img"
        val file = safeStoreFile(fileName)
        val tempFile = safeStoreFile("$fileName.${UUID.randomUUID()}.tmp")

        if (file.exists() && file.length() == sourceFile.length() && file.sha256Hex() == sha256) {
            deleteUserFaceFiles(userId = userId, keepFileName = fileName)
            return@withContext FaceCacheRecord(
                fileName = fileName,
                sha256 = sha256,
                createdAtMillis = System.currentTimeMillis(),
                sizeBytes = sourceFile.length(),
            )
        }

        try {
            sourceFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.length() != sourceFile.length() || tempFile.sha256Hex() != sha256) {
                throw IOException("Temporary face cache file integrity check failed.")
            }
            if (file.exists() && !file.delete()) {
                throw IOException("Failed to replace existing face cache file.")
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = false)
                tempFile.deleteIfExistsOrLog()
            }
        } catch (e: Exception) {
            tempFile.deleteIfExists(suppressedBy = e)
            throw e
        }

        deleteUserFaceFiles(userId = userId, keepFileName = fileName)

        FaceCacheRecord(
            fileName = fileName,
            sha256 = sha256,
            createdAtMillis = System.currentTimeMillis(),
            sizeBytes = sourceFile.length(),
        )
    }

    suspend fun readFaceFile(record: FaceCacheRecord): File = withContext(ioDispatcher) {
        val file = safeStoreFile(record.fileName)
        if (!file.exists()) {
            throw IOException("Face cache file is missing.")
        }
        if (file.length() != record.sizeBytes || file.sha256Hex() != record.sha256) {
            throw IOException("Face cache file integrity check failed.")
        }
        file
    }

    suspend fun deleteUserFaceFiles(userId: Int) = withContext(ioDispatcher) {
        deleteUserFaceFiles(userId = userId, keepFileName = null)
    }

    private fun deleteUserFaceFiles(
        userId: Int,
        keepFileName: String?,
    ) {
        val prefix = "face_user_${userId}_"
        storeDir().listFiles { file ->
            file.isFile && file.name.startsWith(prefix) && file.name != keepFileName
        }?.forEach { file ->
            if (!file.delete()) {
                throw IOException("Failed to delete face cache file: ${file.name}")
            }
        }
    }

    private fun File.deleteIfExists(suppressedBy: Exception? = null) {
        if (!exists() || delete()) return

        val exception = IOException("Failed to remove temporary face cache file.")
        if (suppressedBy == null) {
            throw exception
        } else {
            suppressedBy.addSuppressed(exception)
        }
    }

    private fun File.deleteIfExistsOrLog() {
        try {
            deleteIfExists()
        } catch (e: Exception) {
            logE("删除临时人脸缓存文件失败: $absolutePath", tag = TAG, throwable = e)
        }
    }

    private fun safeStoreFile(fileName: String): File {
        require(fileName.matches(FACE_FILE_NAME_PATTERN) || fileName.matches(TEMP_FACE_FILE_NAME_PATTERN)) {
            "Invalid face cache file name."
        }
        return File(storeDir(), fileName)
    }

    private fun storeDir(): File {
        return File(context.filesDir, FACE_STORE_DIR).apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Failed to create face cache directory.")
            }
        }
    }

    private companion object {
        const val TAG = "FaceFileStore"
        const val FACE_STORE_DIR = "face_store"
        val FACE_FILE_NAME_PATTERN = Regex("""face_user_\d+_[A-Fa-f0-9]{64}\.img""")
        val TEMP_FACE_FILE_NAME_PATTERN = Regex("""face_user_\d+_[A-Fa-f0-9]{64}\.img\.[A-Fa-f0-9-]+\.tmp""")
    }
}

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String {
    return buildString(size * 2) {
        this@toHexString.forEach { byte ->
            append("%02X".format(byte))
        }
    }
}
