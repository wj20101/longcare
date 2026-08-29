package com.ytone.longcare.data.userstorage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ytone.longcare.domain.facecache.UserFaceArtifactStorage
import javax.inject.Inject
import javax.inject.Singleton

private val faceCacheRecordKey = stringPreferencesKey("face_cache_record_v1")

@Singleton
class ScopedUserFaceArtifactStorage @Inject constructor(
    private val registry: UserStorageRegistry,
    private val managedFiles: UserManagedFiles,
) : UserFaceArtifactStorage {
    override suspend fun clearCurrentFaceArtifacts(expectedUserId: Int) {
        val lease = registry.requireCurrentLease()
        require(lease.scopeKey.userId == expectedUserId) {
            "Face cleanup user does not match the active composite user scope"
        }
        registry.dataStore(lease).edit { preferences -> preferences.remove(faceCacheRecordKey) }
        registry.requireValid(lease)
        managedFiles.clearSessionPurpose(lease, FACE_SESSION_PURPOSE)
    }

    internal companion object {
        const val FACE_SESSION_PURPOSE = "face"
        const val FACE_CACHE_RECORD_KEY = "face_cache_record_v1"
    }
}
