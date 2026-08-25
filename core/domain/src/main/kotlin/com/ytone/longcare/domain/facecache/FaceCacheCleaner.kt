package com.ytone.longcare.domain.facecache

interface FaceCacheCleaner {
    /** Removes every locally persisted face artifact owned by [userId]. */
    suspend fun clearUserFaceArtifacts(userId: Int)
}
