package com.ytone.longcare.data.userstorage

import android.content.Context
import com.ytone.longcare.model.NamespaceId
import com.ytone.longcare.model.UserScopeKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val purposePattern = Regex("[a-z0-9][a-z0-9_-]{0,63}")

class UserNamespacePaths internal constructor(
    val scopeKey: UserScopeKey,
    val namespaceId: NamespaceId,
    val databaseFile: File,
    val dataStoreFile: File,
    val namespaceRoot: File,
    val metadataFile: File,
    val persistentRoot: File,
    val sessionRoot: File,
) {
    fun persistentFile(purpose: String, relativePath: String): File =
        resolveUnder(persistentRoot, purpose, relativePath)

    fun sessionFile(purpose: String, relativePath: String): File =
        resolveUnder(sessionRoot, purpose, relativePath)

    fun persistentPurposeRoot(purpose: String): File = purposeRoot(persistentRoot, purpose)

    fun sessionPurposeRoot(purpose: String): File = purposeRoot(sessionRoot, purpose)

    private fun resolveUnder(root: File, purpose: String, relativePath: String): File {
        require(relativePath.isNotBlank()) { "relativePath must not be blank" }
        require(!File(relativePath).isAbsolute) { "absolute paths are not allowed" }
        val purposeRoot = purposeRoot(root, purpose)
        val resolved = File(purposeRoot, relativePath).canonicalFile
        require(resolved.isWithin(purposeRoot)) { "path escapes user namespace" }
        return resolved
    }

    private fun purposeRoot(root: File, purpose: String): File {
        require(purposePattern.matches(purpose)) { "invalid file purpose" }
        val canonicalRoot = root.canonicalFile
        val result = File(canonicalRoot, purpose).canonicalFile
        require(result.isWithin(canonicalRoot)) { "purpose escapes user namespace" }
        return result
    }
}

@Singleton
class UserNamespacePathsFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun forScope(scopeKey: UserScopeKey): UserNamespacePaths {
        val namespaceId = scopeKey.namespaceId()
        val digest = namespaceId.value.removePrefix("v1_")
        val namespaceRoot = File(context.filesDir, "user_scopes/v1/$digest")
        return UserNamespacePaths(
            scopeKey = scopeKey,
            namespaceId = namespaceId,
            databaseFile = context.getDatabasePath("longcare_user_${namespaceId.value}.db"),
            dataStoreFile = File(context.filesDir, "datastore/user_${namespaceId.value}.preferences_pb"),
            namespaceRoot = namespaceRoot,
            metadataFile = File(namespaceRoot, "namespace.json"),
            persistentRoot = File(namespaceRoot, "persistent"),
            sessionRoot = File(context.cacheDir, "user_scopes/v1/$digest/session"),
        )
    }
}

internal fun File.isWithin(root: File): Boolean {
    val canonicalRoot = root.canonicalFile
    val canonicalTarget = canonicalFile
    return canonicalTarget == canonicalRoot || canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)
}
