package com.ytone.longcare.data.userstorage

import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManagedSessionFilesCleanupHook @Inject constructor(
    private val managedFiles: UserManagedFiles,
) : SessionRuntimeCleanupHook {
    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        managedFiles.clearRevokedSessionFiles(identity.scopeKey)
    }
}
