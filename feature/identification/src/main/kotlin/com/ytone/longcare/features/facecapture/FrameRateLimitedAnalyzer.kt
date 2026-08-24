package com.ytone.longcare.features.facecapture

import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Limits detector work while preserving the delegate analyzer's CameraX configuration contract.
 * Skipped frames are closed immediately so preview and still capture remain non-blocking.
 */
internal class FrameRateLimitedAnalyzer(
    private val delegate: ImageAnalysis.Analyzer,
    targetFramesPerSecond: Int,
) : ImageAnalysis.Analyzer {
    private val frameGate = AnalysisFrameGate(
        minimumFrameIntervalNanos = frameIntervalNanos(targetFramesPerSecond),
    )

    override fun analyze(image: ImageProxy) {
        if (frameGate.shouldAnalyze(image.imageInfo.timestamp)) {
            delegate.analyze(image)
        } else {
            image.close()
        }
    }

    override fun getDefaultTargetResolution(): Size? = delegate.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem

    override fun updateTransform(matrix: Matrix?) {
        delegate.updateTransform(matrix)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L

        fun frameIntervalNanos(targetFramesPerSecond: Int): Long {
            require(targetFramesPerSecond > 0)
            return NANOS_PER_SECOND / targetFramesPerSecond
        }
    }
}

internal class AnalysisFrameGate(
    private val minimumFrameIntervalNanos: Long,
) {
    private var lastFrameTimestampNanos: Long? = null
    private var nextAnalysisTimestampNanos: Long? = null

    init {
        require(minimumFrameIntervalNanos > 0L)
    }

    @Synchronized
    fun shouldAnalyze(timestampNanos: Long): Boolean {
        val previousFrameTimestamp = lastFrameTimestampNanos
        val nextAnalysisTimestamp = nextAnalysisTimestampNanos
        if (
            previousFrameTimestamp == null ||
            nextAnalysisTimestamp == null ||
            timestampNanos <= previousFrameTimestamp
        ) {
            lastFrameTimestampNanos = timestampNanos
            nextAnalysisTimestampNanos = timestampNanos + minimumFrameIntervalNanos
            return true
        }

        lastFrameTimestampNanos = timestampNanos
        if (timestampNanos < nextAnalysisTimestamp) {
            return false
        }

        val elapsedIntervals =
            (timestampNanos - nextAnalysisTimestamp) / minimumFrameIntervalNanos + 1L
        nextAnalysisTimestampNanos =
            nextAnalysisTimestamp + elapsedIntervals * minimumFrameIntervalNanos
        return true
    }
}
