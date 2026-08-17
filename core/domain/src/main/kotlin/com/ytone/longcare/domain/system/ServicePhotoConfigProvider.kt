package com.ytone.longcare.domain.system

/**
 * Supplies the per-category photo limit for the service-photo workflow.
 */
interface ServicePhotoConfigProvider {
    suspend fun getMaxServicePhotoCount(): Int
}
