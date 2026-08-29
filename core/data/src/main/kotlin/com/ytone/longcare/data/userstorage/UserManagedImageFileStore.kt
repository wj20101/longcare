package com.ytone.longcare.data.userstorage

import android.net.Uri
import com.ytone.longcare.common.image.ManagedImageFile
import com.ytone.longcare.common.image.ManagedImageFileStore
import com.ytone.longcare.domain.userstorage.UserStorageLease
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManagedImageFileStore @Inject constructor(
    private val storageRegistry: UserStorageRegistry,
    private val managedFiles: UserManagedFiles,
) : ManagedImageFileStore {
    override fun createSessionFile(
        purpose: String,
        filePrefix: String,
        suffix: String,
    ): ManagedImageFile {
        require(filePrefixPattern.matches(filePrefix)) { "invalid managed image prefix" }
        require(suffixPattern.matches(suffix)) { "invalid managed image suffix" }
        val lease = storageRegistry.requireCurrentLease()
        val relativePath =
            "${filePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$suffix"
        val file = managedFiles.sessionFile(lease, purpose, relativePath)
        val parent = file.parentFile ?: throw IOException("Managed image directory is unavailable")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Managed image directory could not be created")
        }
        storageRegistry.requireValid(lease)
        return OwnedReference(lease, purpose, file)
    }

    override fun requireCurrent(reference: ManagedImageFile) {
        val owned = reference.requireOwnedReference()
        managedFiles.requireCurrentSessionFile(owned.lease, owned.purpose, owned.file)
    }

    override fun deleteOwned(reference: ManagedImageFile): Boolean {
        val owned = reference.requireOwnedReference()
        return managedFiles.deleteOwnedSessionFile(owned.lease, owned.purpose, owned.file)
    }

    override fun deleteCurrentSessionFile(
        uri: Uri,
        allowedPurposes: Set<String>,
    ): Boolean = managedFiles.deleteCurrentSessionFile(
        lease = storageRegistry.requireCurrentLease(),
        uri = uri,
        allowedPurposes = allowedPurposes,
    )

    override fun requireCurrentUserFile(uri: Uri): File = managedFiles.requireCurrentUserFile(
        lease = storageRegistry.requireCurrentLease(),
        uri = uri,
    )

    override fun listCurrentSessionFiles(purpose: String): List<File> =
        managedFiles.listCurrentSessionFiles(storageRegistry.requireCurrentLease(), purpose)

    private fun ManagedImageFile.requireOwnedReference(): OwnedReference =
        this as? OwnedReference
            ?: throw IllegalArgumentException("Unrecognized managed image reference")

    private data class OwnedReference(
        val lease: UserStorageLease,
        val purpose: String,
        override val file: File,
    ) : ManagedImageFile

    private companion object {
        val filePrefixPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        val suffixPattern = Regex("\\.[a-z0-9]{1,10}")
    }
}
