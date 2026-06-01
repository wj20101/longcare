package com.ytone.longcare.features.identification.data

import android.content.Context
import com.ytone.longcare.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
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
    suspend fun writeFaceBytes(
        userId: Int,
        bytes: ByteArray,
    ): FaceCacheRecord = withContext(ioDispatcher) {
        require(bytes.isNotEmpty()) { "Face image bytes must not be empty." }

        val sha256 = bytes.sha256Hex()
        val fileName = "face_user_${userId}_$sha256.img"
        val file = safeStoreFile(fileName)
        val tempFile = safeStoreFile("$fileName.tmp")

        tempFile.writeBytes(bytes)
        if (file.exists() && !file.delete()) {
            throw IOException("Failed to replace existing face cache file.")
        }
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            if (!tempFile.delete()) {
                throw IOException("Failed to remove temporary face cache file.")
            }
        }

        deleteUserFaceFiles(userId = userId, keepFileName = fileName)

        FaceCacheRecord(
            fileName = fileName,
            sha256 = sha256,
            createdAtMillis = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong(),
        )
    }

    suspend fun readFaceBytes(record: FaceCacheRecord): ByteArray = withContext(ioDispatcher) {
        val file = safeStoreFile(record.fileName)
        if (!file.exists()) {
            throw IOException("Face cache file is missing.")
        }
        val bytes = file.readBytes()
        if (bytes.size.toLong() != record.sizeBytes || bytes.sha256Hex() != record.sha256) {
            throw IOException("Face cache file integrity check failed.")
        }
        bytes
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
        const val FACE_STORE_DIR = "face_store"
        val FACE_FILE_NAME_PATTERN = Regex("""face_user_\d+_[A-Fa-f0-9]{64}\.img""")
        val TEMP_FACE_FILE_NAME_PATTERN = Regex("""face_user_\d+_[A-Fa-f0-9]{64}\.img\.tmp""")
    }
}

private fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append("%02X".format(byte))
        }
    }
}
