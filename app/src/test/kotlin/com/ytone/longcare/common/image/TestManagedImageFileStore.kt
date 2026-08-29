package com.ytone.longcare.common.image

import android.net.Uri
import java.io.File
import java.util.UUID

internal class TestManagedImageFileStore(
    private val root: File,
) : ManagedImageFileStore {
    private var generation = 1L
    var loggedIn: Boolean = true
    var invalidateOnValidationCall: Int? = null
    private var validationCalls = 0

    override fun createSessionFile(
        purpose: String,
        filePrefix: String,
        suffix: String,
    ): ManagedImageFile {
        check(loggedIn) { "User storage is not ready" }
        val file = File(
            generationRoot(generation).resolve(purpose),
            "${filePrefix}_${UUID.randomUUID()}$suffix",
        )
        check(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        return Reference(generation, purpose, file)
    }

    override fun requireCurrent(reference: ManagedImageFile) {
        val owned = reference as? Reference ?: error("Unknown test reference")
        validationCalls += 1
        if (invalidateOnValidationCall == validationCalls) switchUser()
        check(loggedIn && owned.generation == generation) { "Expired user storage lease" }
    }

    override fun deleteOwned(reference: ManagedImageFile): Boolean {
        val owned = reference as? Reference ?: error("Unknown test reference")
        return owned.file.exists() && owned.file.delete()
    }

    override fun deleteCurrentSessionFile(
        uri: Uri,
        allowedPurposes: Set<String>,
    ): Boolean {
        if (!loggedIn || (uri.scheme != null && uri.scheme != "file")) return false
        val candidate = uri.path?.let(::File)?.canonicalFile ?: return false
        val isOwned = allowedPurposes.any { purpose ->
            candidate.isInside(generationRoot(generation).resolve(purpose).canonicalFile)
        }
        return isOwned && candidate.isFile && candidate.delete()
    }

    override fun requireCurrentUserFile(uri: Uri): File {
        check(loggedIn && (uri.scheme == null || uri.scheme == "file")) {
            "User storage is not ready"
        }
        val candidate = File(requireNotNull(uri.path)).canonicalFile
        check(candidate.isInside(generationRoot(generation).canonicalFile) && candidate.isFile) {
            "File belongs to an expired user storage lease"
        }
        return candidate
    }

    override fun listCurrentSessionFiles(purpose: String): List<File> {
        check(loggedIn) { "User storage is not ready" }
        return generationRoot(generation).resolve(purpose).listFiles(File::isFile).orEmpty().toList()
    }

    fun switchUser() {
        generation += 1
        loggedIn = true
    }

    fun currentRoot(): File = generationRoot(generation)

    private fun generationRoot(value: Long) = root.resolve("generation_$value")

    private data class Reference(
        val generation: Long,
        val purpose: String,
        override val file: File,
    ) : ManagedImageFile
}

private fun File.isInside(root: File): Boolean {
    val candidate = canonicalFile
    val canonicalRoot = root.canonicalFile
    return candidate != canonicalRoot &&
        candidate.path.startsWith(canonicalRoot.path + File.separator)
}
