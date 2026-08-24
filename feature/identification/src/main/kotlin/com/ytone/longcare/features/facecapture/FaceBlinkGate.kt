package com.ytone.longcare.features.facecapture

import kotlin.math.max
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
 * Verifies one complete blink against a short baseline of the current user's naturally open eyes.
 *
 * ML Kit eye probabilities vary significantly with eye shape, camera angle, and device. Relative
 * drops avoid rejecting users whose normal open-eye score never reaches a fixed high threshold.
 * A confidence floor, stable baseline, both-eye checks, tracking continuity, and stable reopening
 * still prevent a noisy frame or wink from completing the liveness sequence.
 */
internal class FaceBlinkGate(
    private val requiredBaselineSamples: Int = REQUIRED_BASELINE_SAMPLES,
    private val minimumReliableOpenProbability: Float = MINIMUM_RELIABLE_OPEN_PROBABILITY,
    private val maximumBaselineRange: Float = MAXIMUM_BASELINE_RANGE,
    private val minimumClosedDrop: Float = MINIMUM_CLOSED_DROP,
    private val closedBaselineRatio: Float = CLOSED_BASELINE_RATIO,
    private val strongClosedProbability: Float = STRONG_CLOSED_PROBABILITY,
    private val strongClosedDrop: Float = STRONG_CLOSED_DROP,
    private val strongClosedBaselineRatio: Float = STRONG_CLOSED_BASELINE_RATIO,
    private val reopenBaselineRatio: Float = REOPEN_BASELINE_RATIO,
    private val requiredClosedSamples: Int = REQUIRED_CLOSED_SAMPLES,
    private val requiredReopenSamples: Int = REQUIRED_REOPEN_SAMPLES,
    private val requiredReopenDurationMillis: Long = REQUIRED_REOPEN_DURATION_MILLIS,
    private val maximumClosedDurationMillis: Long = MAXIMUM_CLOSED_DURATION_MILLIS,
) {
    private var activeTrackingId: Int? = null
    private var stage = FaceBlinkStage.WAITING_FOR_OPEN_EYES
    private val leftBaselineSamples = mutableListOf<Float>()
    private val rightBaselineSamples = mutableListOf<Float>()
    private var leftOpenBaseline: Float? = null
    private var rightOpenBaseline: Float? = null
    private var closedSampleCount = 0
    private var reopenSampleCount = 0
    private var closedConfirmedAtMillis: Long? = null
    private var reopenStartedAtMillis: Long? = null
    private var lastTimestampMillis: Long? = null

    init {
        require(requiredBaselineSamples > 1)
        require(minimumReliableOpenProbability in 0f..1f)
        require(maximumBaselineRange in 0f..1f)
        require(minimumClosedDrop in 0f..1f)
        require(closedBaselineRatio in 0f..1f)
        require(strongClosedProbability in 0f..1f)
        require(strongClosedDrop >= minimumClosedDrop)
        require(strongClosedBaselineRatio in 0f..closedBaselineRatio)
        require(reopenBaselineRatio in closedBaselineRatio..1f)
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

        if (stage == FaceBlinkStage.WAITING_FOR_OPEN_EYES) {
            return observeInitialOpenEyes(leftEyeOpen, rightEyeOpen)
        }

        val leftBaseline = leftOpenBaseline
        val rightBaseline = rightOpenBaseline
        if (leftBaseline == null || rightBaseline == null) {
            resetProgressLocked()
            return observeInitialOpenEyes(leftEyeOpen, rightEyeOpen)
        }

        val bothEyesClosed = isEyeClosed(leftEyeOpen, leftBaseline) &&
            isEyeClosed(rightEyeOpen, rightBaseline)
        val bothEyesStronglyClosed = isEyeStronglyClosed(leftEyeOpen, leftBaseline) &&
            isEyeStronglyClosed(rightEyeOpen, rightBaseline)
        val bothEyesReopened = isEyeReopened(leftEyeOpen, leftBaseline) &&
            isEyeReopened(rightEyeOpen, rightBaseline)

        return when (stage) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> observeInitialOpenEyes(
                leftEyeOpen = leftEyeOpen,
                rightEyeOpen = rightEyeOpen,
            )
            FaceBlinkStage.WAITING_FOR_BLINK -> observeBlink(
                bothEyesClosed = bothEyesClosed,
                bothEyesStronglyClosed = bothEyesStronglyClosed,
            )
            FaceBlinkStage.WAITING_FOR_REOPEN -> observeReopen(
                bothEyesReopened = bothEyesReopened,
                leftEyeOpen = leftEyeOpen,
                rightEyeOpen = rightEyeOpen,
                timestampMillis = observation.timestampMillis,
            )
            FaceBlinkStage.VERIFYING_REOPEN -> verifyStableReopen(
                bothEyesReopened = bothEyesReopened,
                leftEyeOpen = leftEyeOpen,
                rightEyeOpen = rightEyeOpen,
                timestampMillis = observation.timestampMillis,
            )
            FaceBlinkStage.COMPLETE -> currentResult()
        }
    }

    @Synchronized
    fun reset() {
        resetLocked()
    }

    private fun observeInitialOpenEyes(
        leftEyeOpen: Float,
        rightEyeOpen: Float,
    ): FaceBlinkResult {
        if (
            leftEyeOpen < minimumReliableOpenProbability ||
            rightEyeOpen < minimumReliableOpenProbability
        ) {
            clearBaselineSamples()
            return currentResult()
        }

        leftBaselineSamples += leftEyeOpen
        rightBaselineSamples += rightEyeOpen
        if (!baselineSamplesAreStable()) {
            clearBaselineSamples()
            leftBaselineSamples += leftEyeOpen
            rightBaselineSamples += rightEyeOpen
        }

        if (leftBaselineSamples.size >= requiredBaselineSamples) {
            leftOpenBaseline = leftBaselineSamples.median()
            rightOpenBaseline = rightBaselineSamples.median()
            stage = FaceBlinkStage.WAITING_FOR_BLINK
            return currentResult()
        }

        return currentResult(
            progress = INITIAL_OPEN_PROGRESS *
                leftBaselineSamples.size.toFloat() / requiredBaselineSamples,
        )
    }

    private fun observeBlink(
        bothEyesClosed: Boolean,
        bothEyesStronglyClosed: Boolean,
    ): FaceBlinkResult {
        closedSampleCount = when {
            bothEyesStronglyClosed -> requiredClosedSamples
            bothEyesClosed -> closedSampleCount + 1
            else -> 0
        }
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
        bothEyesReopened: Boolean,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        timestampMillis: Long,
    ): FaceBlinkResult {
        if (closedSequenceTimedOut(timestampMillis)) {
            resetProgressLocked()
            return observeInitialOpenEyes(leftEyeOpen, rightEyeOpen)
        }

        if (bothEyesReopened) {
            stage = FaceBlinkStage.VERIFYING_REOPEN
            reopenStartedAtMillis = timestampMillis
            reopenSampleCount = 1
        }
        return currentResult()
    }

    private fun verifyStableReopen(
        bothEyesReopened: Boolean,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        timestampMillis: Long,
    ): FaceBlinkResult {
        if (closedSequenceTimedOut(timestampMillis)) {
            resetProgressLocked()
            return observeInitialOpenEyes(leftEyeOpen, rightEyeOpen)
        }

        if (!bothEyesReopened) {
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

    private fun isEyeClosed(
        probability: Float,
        baseline: Float,
    ): Boolean =
        baseline - probability >= minimumClosedDrop &&
            probability <= baseline * closedBaselineRatio

    private fun isEyeStronglyClosed(
        probability: Float,
        baseline: Float,
    ): Boolean =
        probability <= strongClosedProbability &&
            baseline - probability >= strongClosedDrop &&
            probability <= baseline * strongClosedBaselineRatio

    private fun isEyeReopened(
        probability: Float,
        baseline: Float,
    ): Boolean = probability >= max(
        minimumReliableOpenProbability,
        baseline * reopenBaselineRatio,
    )

    private fun baselineSamplesAreStable(): Boolean =
        leftBaselineSamples.range() <= maximumBaselineRange &&
            rightBaselineSamples.range() <= maximumBaselineRange

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
        clearBaselineSamples()
        leftOpenBaseline = null
        rightOpenBaseline = null
        closedSampleCount = 0
        reopenSampleCount = 0
        closedConfirmedAtMillis = null
        reopenStartedAtMillis = null
    }

    private fun clearBaselineSamples() {
        leftBaselineSamples.clear()
        rightBaselineSamples.clear()
    }

    private fun List<Float>.median(): Float = sorted()[size / 2]

    private fun List<Float>.range(): Float =
        if (isEmpty()) 0f else requireNotNull(maxOrNull()) - requireNotNull(minOrNull())

    private val FaceBlinkStage.baseProgress: Float
        get() = when (this) {
            FaceBlinkStage.WAITING_FOR_OPEN_EYES -> 0f
            FaceBlinkStage.WAITING_FOR_BLINK -> WAITING_FOR_BLINK_PROGRESS
            FaceBlinkStage.WAITING_FOR_REOPEN -> REOPEN_PROGRESS_START
            FaceBlinkStage.VERIFYING_REOPEN -> REOPEN_PROGRESS_START
            FaceBlinkStage.COMPLETE -> 1f
        }

    internal companion object {
        const val REQUIRED_BASELINE_SAMPLES = 3
        const val MINIMUM_RELIABLE_OPEN_PROBABILITY = 0.35f
        const val MAXIMUM_BASELINE_RANGE = 0.16f
        const val MINIMUM_CLOSED_DROP = 0.18f
        const val CLOSED_BASELINE_RATIO = 0.55f
        const val STRONG_CLOSED_PROBABILITY = 0.18f
        const val STRONG_CLOSED_DROP = 0.25f
        const val STRONG_CLOSED_BASELINE_RATIO = 0.4f
        const val REOPEN_BASELINE_RATIO = 0.78f
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
