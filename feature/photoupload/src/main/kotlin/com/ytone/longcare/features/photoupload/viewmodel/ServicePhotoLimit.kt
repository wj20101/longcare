package com.ytone.longcare.features.photoupload.viewmodel

data class ServicePhotoLimitState(
    val isLoaded: Boolean = false,
    val maxPhotosPerCategory: Int? = null,
)

sealed interface PhotoProcessingEvent {
    data class PhotoLimitReached(val maxCount: Int) : PhotoProcessingEvent
}

internal data class AddImagesResult(
    val rejectedCount: Int,
)

internal object ServicePhotoLimitPolicy {
    fun normalize(configuredMax: Int): Int? = configuredMax.takeIf { it > 0 }

    fun canAdd(currentCount: Int, maxCount: Int?): Boolean =
        maxCount == null || currentCount < maxCount

    fun allowedIncomingCount(
        currentCount: Int,
        requestedCount: Int,
        maxCount: Int?,
    ): Int {
        val safeRequestedCount = requestedCount.coerceAtLeast(0)
        if (maxCount == null) return safeRequestedCount

        val remainingCount = (maxCount - currentCount.coerceAtLeast(0)).coerceAtLeast(0)
        return minOf(safeRequestedCount, remainingCount)
    }
}
