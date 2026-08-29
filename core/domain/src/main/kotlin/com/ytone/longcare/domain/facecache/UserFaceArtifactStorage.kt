package com.ytone.longcare.domain.facecache

/** Current-user storage boundary used by face capture without exposing DataStore or file paths. */
interface UserFaceArtifactStorage {
    suspend fun clearCurrentFaceArtifacts(expectedUserId: Int)
}
