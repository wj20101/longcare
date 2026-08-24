package com.ytone.longcare.features.facecapture

import kotlin.math.roundToInt

typealias FaceDetectionCallback = (snapshot: FaceDetectionSnapshot) -> Unit

data class FaceDetectionSnapshot(
    val detected: Boolean,
    val positionQualified: Boolean,
    val confirmationProgress: Float,
    val hint: FaceCaptureHint,
)

/**
 * Converts noisy per-frame detector output into stable states rendered by the UI.
 *
 * Safety-sensitive blink state is reset immediately by [FaceBlinkGate]. Presentation changes are
 * intentionally slower: a transient lost face, position change, or eye-probability fluctuation
 * must not make the visible instruction jump between stages. A real regression is still shown
 * after it persists for a short period, while forward blink confirmation is delivered immediately.
 */
internal class FaceDetectionStatePublisher(
    private val callback: FaceDetectionCallback,
) {
    private var displayedSnapshot: FaceDetectionSnapshot? = null
    private var pendingSnapshot: FaceDetectionSnapshot? = null
    private var pendingSinceMillis: Long? = null
    private var progressRegressionSinceMillis: Long? = null
    private var lastTimestampMillis: Long? = null

    @Synchronized
    fun publish(
        detected: Boolean,
        positionQualified: Boolean,
        progress: Float,
        hint: FaceCaptureHint,
        timestampMillis: Long,
    ) {
        if (lastTimestampMillis?.let { timestampMillis < it } == true) {
            clearPendingState()
        }
        lastTimestampMillis = timestampMillis

        val candidate = FaceDetectionSnapshot(
            detected = detected,
            positionQualified = detected && positionQualified,
            confirmationProgress = progress.toUiProgress(),
            hint = hint,
        )
        val displayed = displayedSnapshot
        if (displayed == null) {
            emit(candidate)
            return
        }

        if (candidate == displayed) {
            clearPendingState()
            return
        }

        when {
            candidate.confirmationProgress > displayed.confirmationProgress -> {
                clearPendingState()
                emit(candidate)
            }

            candidate.confirmationProgress < displayed.confirmationProgress -> {
                publishPersistentProgressRegression(candidate, timestampMillis)
            }

            else -> publishStableCandidate(candidate, timestampMillis)
        }
    }

    private fun publishPersistentProgressRegression(
        candidate: FaceDetectionSnapshot,
        timestampMillis: Long,
    ) {
        pendingSnapshot = candidate
        pendingSinceMillis = null

        val regressionStartedAt = progressRegressionSinceMillis ?: timestampMillis.also {
            progressRegressionSinceMillis = it
        }
        if (timestampMillis.elapsedSince(regressionStartedAt) >= PROGRESS_RESET_DELAY_MILLIS) {
            clearPendingState()
            emit(candidate)
        }
    }

    private fun publishStableCandidate(
        candidate: FaceDetectionSnapshot,
        timestampMillis: Long,
    ) {
        progressRegressionSinceMillis = null
        if (pendingSnapshot != candidate) {
            pendingSnapshot = candidate
            pendingSinceMillis = timestampMillis
            return
        }

        val candidateStartedAt = pendingSinceMillis ?: timestampMillis.also {
            pendingSinceMillis = it
        }
        if (timestampMillis.elapsedSince(candidateStartedAt) >= UI_STATE_STABILITY_MILLIS) {
            clearPendingState()
            emit(candidate)
        }
    }

    private fun emit(snapshot: FaceDetectionSnapshot) {
        if (snapshot == displayedSnapshot) return

        displayedSnapshot = snapshot
        callback(snapshot)
    }

    @Synchronized
    fun reset() {
        displayedSnapshot = null
        lastTimestampMillis = null
        clearPendingState()
    }

    private fun clearPendingState() {
        pendingSnapshot = null
        pendingSinceMillis = null
        progressRegressionSinceMillis = null
    }

    private fun Long.elapsedSince(earlierMillis: Long): Long =
        (this - earlierMillis).coerceAtLeast(0L)

    private fun Float.toUiProgress(): Float =
        (coerceIn(0f, 1f) * UI_PROGRESS_STEPS)
            .roundToInt()
            .toFloat() / UI_PROGRESS_STEPS

    private companion object {
        const val UI_PROGRESS_STEPS = 20
        const val UI_STATE_STABILITY_MILLIS = 350L
        const val PROGRESS_RESET_DELAY_MILLIS = 450L
    }
}
