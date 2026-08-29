package com.ytone.longcare.data.userstorage

import android.content.Context
import android.net.Uri
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.UserScopeKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class UserManagedFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pathsFactory: UserNamespacePathsFactory,
    private val storageRegistry: UserStorageRegistry,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @JvmInline
    value class PersistentHandle internal constructor(val value: String)

    fun persistentFile(lease: UserStorageLease, purpose: String, relativePath: String): File {
        storageRegistry.requireValid(lease)
        return pathsFactory.forScope(lease.scopeKey).persistentFile(purpose, relativePath)
    }

    fun sessionFile(lease: UserStorageLease, purpose: String, relativePath: String): File {
        storageRegistry.requireValid(lease)
        return pathsFactory.forScope(lease.scopeKey).sessionFile(purpose, relativePath)
    }

    /** Validates a file source against the complete current user-session root. */
    fun requireCurrentSessionFile(lease: UserStorageLease, file: File): File {
        storageRegistry.requireValid(lease)
        val root = pathsFactory.forScope(lease.scopeKey).sessionRoot.canonicalFile
        val candidate = file.canonicalFile
        require(candidate != root && candidate.isWithin(root) && candidate.isFile) {
            "file is outside the current user session"
        }
        return candidate
    }

    fun requireCurrentUserFile(lease: UserStorageLease, uri: Uri): File {
        storageRegistry.requireValid(lease)
        require(uri.scheme == null || uri.scheme == "file") { "managed user source must be a file URI" }
        val candidate = File(requireNotNull(uri.path) { "managed user source has no path" }).canonicalFile
        val paths = pathsFactory.forScope(lease.scopeKey)
        val isOwned = listOf(paths.sessionRoot, paths.persistentRoot).any { root ->
            candidate != root.canonicalFile && candidate.isWithin(root)
        }
        require(isOwned && candidate.isFile) { "file is outside the current user namespace" }
        return candidate
    }

    fun persistentHandle(
        lease: UserStorageLease,
        purpose: String,
        file: File,
    ): PersistentHandle {
        storageRegistry.requireValid(lease)
        return persistentHandleUnchecked(lease, purpose, file)
    }

    fun resolvePersistentFile(
        lease: UserStorageLease,
        handle: PersistentHandle,
    ): File {
        storageRegistry.requireValid(lease)
        return resolvePersistentFileUnchecked(lease, handle)
    }

    suspend fun importPersistentFile(
        lease: UserStorageLease,
        purpose: String,
        relativePath: String,
        source: Uri,
    ): PersistentHandle = withContext(ioDispatcher) {
        storageRegistry.requireValid(lease)
        val target = pathsFactory.forScope(lease.scopeKey).persistentFile(purpose, relativePath)
        val sourceFile = source.path
            ?.takeIf { source.scheme == null || source.scheme == "file" }
            ?.let(::File)
        if (sourceFile != null && sourceFile.exists() && sourceFile.canonicalFile == target.canonicalFile) {
            return@withContext persistentHandleUnchecked(lease, purpose, target)
        }

        target.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) { "Unable to create managed file directory" }
        }
        val temporary = File(target.parentFile, ".${target.name}.importing")
        var committed = false
        try {
            openInput(source).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            storageRegistry.requireValid(lease)
            check(!target.exists()) { "Managed file destination already exists" }
            check(temporary.renameTo(target)) { "Unable to commit managed file" }
            committed = true
            persistentHandleUnchecked(lease, purpose, target)
        } finally {
            temporary.delete()
            if (!committed) target.delete()
        }
    }

    suspend fun deletePersistentFiles(
        lease: UserStorageLease,
        handles: Iterable<PersistentHandle>,
    ) = withContext(ioDispatcher) {
        storageRegistry.requireValid(lease)
        handles.forEach { handle -> resolvePersistentFileUnchecked(lease, handle).delete() }
    }

    internal suspend fun rollbackImportedFile(
        lease: UserStorageLease,
        handle: PersistentHandle,
    ) = withContext(ioDispatcher) {
        resolvePersistentFileUnchecked(lease, handle).delete()
    }

    suspend fun clearSessionPurpose(lease: UserStorageLease, purpose: String) = withContext(ioDispatcher) {
        storageRegistry.requireValid(lease)
        pathsFactory.forScope(lease.scopeKey).sessionPurposeRoot(purpose).deleteRecursivelySafely()
    }

    suspend fun clearAllSessionFiles(lease: UserStorageLease) = withContext(ioDispatcher) {
        storageRegistry.requireValid(lease)
        pathsFactory.forScope(lease.scopeKey).sessionRoot.deleteRecursivelySafely()
    }

    /** Trusted lifecycle cleanup used only after the public lease has already been revoked. */
    internal suspend fun clearRevokedSessionFiles(scopeKey: UserScopeKey) = withContext(ioDispatcher) {
        pathsFactory.forScope(scopeKey).sessionRoot.deleteRecursivelySafely()
    }

    internal fun requireCurrentSessionFile(
        lease: UserStorageLease,
        purpose: String,
        file: File,
    ) {
        storageRegistry.requireValid(lease)
        requireOwnedSessionFile(lease, purpose, file)
    }

    internal fun deleteOwnedSessionFile(
        lease: UserStorageLease,
        purpose: String,
        file: File,
    ): Boolean {
        val owned = requireOwnedSessionFile(lease, purpose, file)
        return owned.exists() && owned.delete()
    }

    internal fun deleteCurrentSessionFile(
        lease: UserStorageLease,
        uri: Uri,
        allowedPurposes: Set<String>,
    ): Boolean {
        storageRegistry.requireValid(lease)
        if (uri.scheme != null && uri.scheme != "file") return false
        val path = uri.path ?: return false
        val candidate = File(path).canonicalFile
        val isOwned = allowedPurposes.any { purpose ->
            val root = pathsFactory.forScope(lease.scopeKey).sessionPurposeRoot(purpose).canonicalFile
            candidate != root && candidate.isWithin(root)
        }
        return isOwned && candidate.isFile && candidate.delete()
    }

    internal fun listCurrentSessionFiles(
        lease: UserStorageLease,
        purpose: String,
    ): List<File> {
        storageRegistry.requireValid(lease)
        return pathsFactory.forScope(lease.scopeKey).sessionPurposeRoot(purpose)
            .listFiles(File::isFile)
            .orEmpty()
            .toList()
    }

    private fun persistentHandleUnchecked(
        lease: UserStorageLease,
        purpose: String,
        file: File,
    ): PersistentHandle {
        val root = pathsFactory.forScope(lease.scopeKey).persistentPurposeRoot(purpose).canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile != root && canonicalFile.isWithin(root)) {
            "file is outside the current persistent purpose"
        }
        val relative = canonicalFile.relativeTo(root).invariantSeparatorsPath
        return PersistentHandle("$PERSISTENT_HANDLE_PREFIX$purpose/$relative")
    }

    private fun resolvePersistentFileUnchecked(
        lease: UserStorageLease,
        handle: PersistentHandle,
    ): File {
        require(handle.value.startsWith(PERSISTENT_HANDLE_PREFIX)) { "unsupported managed file handle" }
        val payload = handle.value.removePrefix(PERSISTENT_HANDLE_PREFIX)
        val purpose = payload.substringBefore('/', missingDelimiterValue = "")
        val relativePath = payload.substringAfter('/', missingDelimiterValue = "")
        require(purpose.isNotBlank() && relativePath.isNotBlank()) { "invalid managed file handle" }
        return pathsFactory.forScope(lease.scopeKey).persistentFile(purpose, relativePath)
    }

    private fun requireOwnedSessionFile(
        lease: UserStorageLease,
        purpose: String,
        file: File,
    ): File {
        val root = pathsFactory.forScope(lease.scopeKey).sessionPurposeRoot(purpose).canonicalFile
        val candidate = file.canonicalFile
        require(candidate != root && candidate.isWithin(root)) {
            "file is outside the owned user session purpose"
        }
        return candidate
    }

    private fun openInput(source: Uri) = when (source.scheme) {
        null, "file" -> {
            val path = requireNotNull(source.path) { "File URI has no path" }
            File(path).inputStream()
        }
        "content", "android.resource" -> requireNotNull(context.contentResolver.openInputStream(source)) {
            "Unable to open source URI"
        }
        else -> throw IllegalArgumentException("Unsupported managed file source scheme")
    }

    private companion object {
        const val PERSISTENT_HANDLE_PREFIX = "v1/persistent/"
    }
}

private fun File.deleteRecursivelySafely() {
    if (!exists()) return
    check(parentFile != null) { "Refusing to delete a root path" }
    if (!deleteRecursively()) {
        throw IllegalStateException("Unable to delete managed user path")
    }
}
