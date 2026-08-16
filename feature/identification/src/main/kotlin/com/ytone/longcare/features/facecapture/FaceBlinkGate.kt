package com.ytone.longcare.features.facecapture

import kotlin.math.min

internal enum class FaceBlinkStage {
    WAITING_FOR_OPEN_EYES,
    WAITING_FOR_BLINK,
    WAITING_FOR_REOPEN,
    VERIFYING_REOPEN,
    COMPLETE,
}

internal data class FaceBlinkObservation(
    val trackingId: Int?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val positionQualified: Boolean,
    val timestampMillis: Long,
)

internal data class FaceBlinkResult(
    val stage: FaceBlinkStage,
    val progress: Float,
    val isReadyToCapture: Boolean,
)

/**
 * Verifies one complete, intentional blink from a continuously tracked face.
 *
 * The state machine uses separate open and closed thresholds (hysteresis), consecutive samples,
 * and a stable reopen period. A wink, one noisy frame, a changed face, or a non-frontal face
 * cannot complete the sequence.
 */
internal class FaceBlinkGate(
    private val openEyeThreshold: Float = OPEN_EYE_THRESHOLD,
    private val closedEyeThreshold: Float = CLOSED_EYE_THRESHOLD,
    private val requiredOpenSamples: Int = REQUIRED_OPEN_SAMPLES,
    private val requiredClosedSamples: Int = REQUIRED_CLOSED_SAMPLES,
    private val requiredReopenSamples: Int = REQUIRED_REOPEN_SAMPLES,
    private val requiredReopenDurationMillis: Long = REQUIRED_REOPEN_DURATION_MILLIS,
    private val maximumClosedDurationMillis: Long = MAXIMUM_CLOSED_DURATION_MILLIS,
) {
    private var activeTrackingId: Int? = null
    private var stage = FaceBlinkStage.WAITING_FOR_OPEN_EYES
    private var openSampleCount = 0
    private var closedSampleCount = 0
    private var reopenSampleCount = 0
    private var closedConfirmedAtMillis: Long? = null
    private var reopenStartedAtMillis: Long? = null
    private var lastTimestampMillis: Long? = null

    init {
        require(openEyeThreshold in 0f..1f)
        require(closedEyeThreshold in 0f..1f)
        require(closedEyeThreshold < openEyeThreshold)
        require(requiredOpenSamples > 0)
        require(requiredClosedSamples > 0)
        require(requiredReopenSamples > 0)
        require(requiredReopenDurationMillis > 0L)
        require(maximumClosedDurationMillis > requiredReopenDurationMillis)
    }

    @Synchronized
    fun evaluate(observation: FaceBlinkObservation): FaceBlinkResult {
        val trackingId = observation.trackingId
        val leftEyeOpen = observation.leftEyeOpenProbability
        val rightEyeOpen = observation.rightEyeOpenProbability
        val probabilitiesAvailable = leftEyeOpen != null &&
            rightEyeOpen != null &&
            leftEyeOpen.isFinite() &&
            rightEyeOpen.isFinite() &&
            leftEyeOpen in 0f..1f &&
            rightEyeOpen in 0f..1f

        if (trackingId == null || !observation.positionQualified || !probabilitiesAvailable) {
            resetLocked()
            return currentResult()
        }

        val previousTimestamp = lastTimestampMillis
        if (previousTimestamp != null && observation.timestampMillis < previousTimestamp) {
            resetLocked()
        }

        if (activeTrackingId != trackingId) {
            resetLocked()
            activeTrackingId = trackingId
        }
        lastTimestampMillis = observation.timestampMillis

        val bothEyesOpen = leftEyeOpen >= openEyeThreshold &&
            rightEyeOpen >= openEyeThreshold
        val bothEyesClosed = leftEyeOpen <= closedEyeThreshold &&
            rightEyeOpen <= closedEyeThreshold

        return when (stage) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> observeInitialOpenEyes(bothEyesOpen)
            FaceBlinkStage.WAITING_FOR_BLINK -> observeBlink(bothEyesClosed)
            FaceBlinkStage.WAITING_FOR_REOPEN -> observeReopen(
                bothEyesOpen = bothEyesOpen,
                timestampMillis = observation.timestampMillis,
            )
            FaceBlinkStage.VERIFYING_REOPEN -> verifyStableReopen(
                bothEyesOpen = bothEyesOpen,
                timestampMillis = observation.timestampMillis,
            )
            FaceBlinkStage.COMPLETE -> currentResult()
        }
    }

    @Synchronized
    fun reset() {
        resetLocked()
    }

    private fun observeInitialOpenEyes(bothEyesOpen: Boolean): FaceBlinkResult {
        openSampleCount = if (bothEyesOpen) openSampleCount + 1 else 0
        if (openSampleCount >= requiredOpenSamples) {
            stage = FaceBlinkStage.WAITING_FOR_BLINK
            return currentResult()
        }
        return currentResult(
            progress = INITIAL_OPEN_PROGRESS * openSampleCount.toFloat() / requiredOpenSamples,
        )
    }

    private fun observeBlink(bothEyesClosed: Boolean): FaceBlinkResult {
        closedSampleCount = if (bothEyesClosed) closedSampleCount + 1 else 0
        if (closedSampleCount >= requiredClosedSamples) {
            stage = FaceBlinkStage.WAITING_FOR_REOPEN
            closedConfirmedAtMillis = requireNotNull(lastTimestampMillis)
            return currentResult()
        }
        return currentResult(
            progress = WAITING_FOR_BLINK_PROGRESS +
                BLINK_PROGRESS_RANGE * closedSampleCount.toFloat() / requiredClosedSamples,
        )
    }

    private fun observeReopen(
        bothEyesOpen: Boolean,
        timestampMillis: Long,
    ): FaceBlinkResult {
        if (closedSequenceTimedOut(timestampMillis)) {
            resetProgressLocked()
            return observeInitialOpenEyes(bothEyesOpen)
        }

        if (bothEyesOpen) {
            stage = FaceBlinkStage.VERIFYING_REOPEN
            reopenStartedAtMillis = timestampMillis
            reopenSampleCount = 1
        }
        return currentResult()
    }

    private fun verifyStableReopen(
        bothEyesOpen: Boolean,
        timestampMillis: Long,
    ): FaceBlinkResult {
        if (closedSequenceTimedOut(timestampMillis)) {
            resetProgressLocked()
            return observeInitialOpenEyes(bothEyesOpen)
        }

        if (!bothEyesOpen) {
            stage = FaceBlinkStage.WAITING_FOR_REOPEN
            reopenStartedAtMillis = null
            reopenSampleCount = 0
            return currentResult()
        }

        reopenSampleCount += 1
        val reopenStartedAt = requireNotNull(reopenStartedAtMillis)
        val elapsedMillis = timestampMillis - reopenStartedAt
        val sampleProgress = reopenSampleCount.toFloat() / requiredReopenSamples
        val durationProgress = elapsedMillis.toFloat() / requiredReopenDurationMillis
        val verificationProgress = min(sampleProgress, durationProgress).coerceIn(0f, 1f)

        if (
            reopenSampleCount >= requiredReopenSamples &&
            elapsedMillis >= requiredReopenDurationMillis
        ) {
            stage = FaceBlinkStage.COMPLETE
            return currentResult()
        }

        return currentResult(
            progress = REOPEN_PROGRESS_START + REOPEN_PROGRESS_RANGE * verificationProgress,
        )
    }

    private fun closedSequenceTimedOut(timestampMillis: Long): Boolean =
        closedConfirmedAtMillis?.let { confirmedAt ->
            timestampMillis - confirmedAt > maximumClosedDurationMillis
        } ?: true

    private fun currentResult(progress: Float = stage.baseProgress): FaceBlinkResult =
        FaceBlinkResult(
            stage = stage,
            progress = progress.coerceIn(0f, 1f),
            isReadyToCapture = stage == FaceBlinkStage.COMPLETE,
        )

    private fun resetLocked() {
        activeTrackingId = null
        lastTimestampMillis = null
        resetProgressLocked()
    }

    private fun resetProgressLocked() {
        stage = FaceBlinkStage.WAITING_FOR_OPEN_EYES
        openSampleCount = 0
        closedSampleCount = 0
        reopenSampleCount = 0
        closedConfirmedAtMillis = null
        reopenStartedAtMillis = null
    }

    private val FaceBlinkStage.baseProgress: Float
        get() = when (this) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> 0f
            FaceBlinkStage.WAITING_FOR_BLINK -> WAITING_FOR_BLINK_PROGRESS
            FaceBlinkStage.WAITING_FOR_REOPEN -> REOPEN_PROGRESS_START
            FaceBlinkStage.VERIFYING_REOPEN -> REOPEN_PROGRESS_START
            FaceBlinkStage.COMPLETE -> 1f
        }

    internal companion object {
        const val OPEN_EYE_THRESHOLD = 0.75f
        const val CLOSED_EYE_THRESHOLD = 0.25f
        const val REQUIRED_OPEN_SAMPLES = 2
        const val REQUIRED_CLOSED_SAMPLES = 2
        const val REQUIRED_REOPEN_SAMPLES = 3
        const val REQUIRED_REOPEN_DURATION_MILLIS = 250L
        const val MAXIMUM_CLOSED_DURATION_MILLIS = 1_500L

        private const val INITIAL_OPEN_PROGRESS = 0.2f
        private const val WAITING_FOR_BLINK_PROGRESS = 0.3f
        private const val BLINK_PROGRESS_RANGE = 0.3f
        private const val REOPEN_PROGRESS_START = 0.65f
        private const val REOPEN_PROGRESS_RANGE = 0.35f
    }
}
