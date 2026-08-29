package com.ytone.longcare.common.image

import android.net.Uri
import java.io.File

/**
 * Opaque reference to an image file created for one exact user-storage lease.
 *
 * Implementations keep the ownership token private; callers only receive the physical file
 * required by CameraX and image codecs.
 */
interface ManagedImageFile {
    val file: File
}

/** User-session file boundary used by [UnifiedImagePipeline]. */
interface ManagedImageFileStore {
    fun createSessionFile(
        purpose: String,
        filePrefix: String,
        suffix: String = ".jpg",
    ): ManagedImageFile

    /** Rejects references whose scope, epoch, or storage generation is no longer current. */
    fun requireCurrent(reference: ManagedImageFile)

    /** Deletes only the exact file represented by an unforgeable reference, even after revocation. */
    fun deleteOwned(reference: ManagedImageFile): Boolean

    /** Deletes a URI only when it belongs to the current lease and one of [allowedPurposes]. */
    fun deleteCurrentSessionFile(
        uri: Uri,
        allowedPurposes: Set<String>,
    ): Boolean

    /** Requires a file URI to belong to the current user's session or persistent root. */
    fun requireCurrentUserFile(uri: Uri): File

    fun listCurrentSessionFiles(purpose: String): List<File>
}
