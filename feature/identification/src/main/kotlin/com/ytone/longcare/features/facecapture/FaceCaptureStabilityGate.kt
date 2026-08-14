package com.ytone.longcare.features.facecapture

import kotlin.math.min

internal data class FaceCaptureStabilityResult(
    val isQualified: Boolean,
    val confirmationProgress: Float,
    val isReadyToCapture: Boolean,
)

/**
 * 将单帧质量判断收口为连续稳定判断。
 *
 * 时间门槛避免连续快速回调被误认为稳定，样本门槛避免两个间隔很远的合格帧直接通过。
 */
internal class FaceCaptureStabilityGate(
    private val minimumQuality: Float = MINIMUM_CAPTURE_QUALITY,
    private val requiredStableDurationMillis: Long = REQUIRED_STABLE_DURATION_MILLIS,
    private val requiredQualifiedSamples: Int = REQUIRED_QUALIFIED_SAMPLES,
) {
    private var firstQualifiedAtMillis: Long? = null
    private var qualifiedSampleCount: Int = 0

    init {
        require(minimumQuality in 0f..1f)
        require(requiredStableDurationMillis > 0L)
        require(requiredQualifiedSamples > 1)
    }

    @Synchronized
    fun evaluate(
        quality: Float,
        timestampMillis: Long,
    ): FaceCaptureStabilityResult {
        if (quality <= minimumQuality) {
            resetLocked()
            return FaceCaptureStabilityResult(
                isQualified = false,
                confirmationProgress = 0f,
                isReadyToCapture = false,
            )
        }

        val startedAt = firstQualifiedAtMillis
            ?.takeIf { timestampMillis >= it }
            ?: timestampMillis.also {
                firstQualifiedAtMillis = it
                qualifiedSampleCount = 0
            }

        qualifiedSampleCount += 1
        val elapsedMillis = timestampMillis - startedAt
        val durationProgress = elapsedMillis.toFloat() / requiredStableDurationMillis
        val sampleProgress = qualifiedSampleCount.toFloat() / requiredQualifiedSamples
        val progress = min(durationProgress, sampleProgress).coerceIn(0f, 1f)

        return FaceCaptureStabilityResult(
            isQualified = true,
            confirmationProgress = progress,
            isReadyToCapture = elapsedMillis >= requiredStableDurationMillis &&
                qualifiedSampleCount >= requiredQualifiedSamples,
        )
    }

    @Synchronized
    fun reset() {
        resetLocked()
    }

    private fun resetLocked() {
        firstQualifiedAtMillis = null
        qualifiedSampleCount = 0
    }

    internal companion object {
        const val MINIMUM_CAPTURE_QUALITY = 0.8f
        const val REQUIRED_STABLE_DURATION_MILLIS = 750L
        const val REQUIRED_QUALIFIED_SAMPLES = 4
    }
}
