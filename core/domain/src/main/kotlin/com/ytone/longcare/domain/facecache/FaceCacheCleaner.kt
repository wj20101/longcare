package com.ytone.longcare.domain.facecache

interface FaceCacheCleaner {
    suspend fun clearUserFaceBase64(userId: Int)
}
