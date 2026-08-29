package com.ytone.longcare.data.userstorage

import android.util.AtomicFile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.ytone.longcare.model.UserScopeKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
internal data class UserNamespaceMetadata(
    val formatVersion: Int,
    val namespaceId: String,
    val companyId: Int,
    val accountId: Int,
    val userId: Int,
) {
    fun matches(scopeKey: UserScopeKey): Boolean =
        formatVersion == CURRENT_FORMAT_VERSION &&
            namespaceId == scopeKey.namespaceId().value &&
            companyId == scopeKey.companyId &&
            accountId == scopeKey.accountId &&
            userId == scopeKey.userId

    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        fun from(scopeKey: UserScopeKey): UserNamespaceMetadata = UserNamespaceMetadata(
            formatVersion = CURRENT_FORMAT_VERSION,
            namespaceId = scopeKey.namespaceId().value,
            companyId = scopeKey.companyId,
            accountId = scopeKey.accountId,
            userId = scopeKey.userId,
        )
    }
}

class NamespaceOwnershipException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

@Singleton
class UserNamespaceMetadataStore @Inject constructor(moshi: Moshi) {
    private val adapter = moshi.adapter(UserNamespaceMetadata::class.java)

    fun verifyOrCreate(paths: UserNamespacePaths) {
        val metadataFile = paths.metadataFile
        if (metadataFile.exists()) {
            val metadata = try {
                adapter.fromJson(metadataFile.readText())
            } catch (error: Exception) {
                throw NamespaceOwnershipException("Namespace metadata is unreadable", error)
            }
            if (metadata == null || !metadata.matches(paths.scopeKey)) {
                throw NamespaceOwnershipException("Namespace metadata does not match requested scope")
            }
            return
        }

        if (containsExistingNamespaceData(paths)) {
            throw NamespaceOwnershipException("Namespace metadata is missing for existing storage")
        }

        metadataFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Unable to create namespace directory")
            }
        }
        val atomicFile = AtomicFile(metadataFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(adapter.toJson(UserNamespaceMetadata.from(paths.scopeKey)).encodeToByteArray())
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw IOException("Unable to persist namespace metadata", error)
        }
    }

    private fun containsExistingNamespaceData(paths: UserNamespacePaths): Boolean =
        paths.databaseFile.exists() ||
            paths.dataStoreFile.exists() ||
            (paths.namespaceRoot.exists() && paths.namespaceRoot.listFiles().orEmpty().isNotEmpty())
}
