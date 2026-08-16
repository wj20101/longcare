package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceBlinkGateTest {
    @Test
    fun `open closed reopen sequence completes blink verification`() {
        val gate = FaceBlinkGate()

        assertThat(gate.observe(0L, left = 0.9f, right = 0.91f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(gate.observe(100L, left = 0.9f, right = 0.91f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(200L, left = 0.1f, right = 0.12f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(300L, left = 0.1f, right = 0.12f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_REOPEN)
        assertThat(gate.observe(400L, left = 0.9f, right = 0.92f).stage)
            .isEqualTo(FaceBlinkStage.VERIFYING_REOPEN)
        assertThat(gate.observe(550L, left = 0.91f, right = 0.9f).isReadyToCapture)
            .isFalse()

        val completed = gate.observe(700L, left = 0.92f, right = 0.93f)

        assertThat(completed.stage).isEqualTo(FaceBlinkStage.COMPLETE)
        assertThat(completed.progress).isEqualTo(1f)
        assertThat(completed.isReadyToCapture).isTrue()
    }

    @Test
    fun `one closed frame is treated as noise`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.9f, right = 0.9f)
        gate.observe(100L, left = 0.9f, right = 0.9f)
        gate.observe(200L, left = 0.1f, right = 0.1f)
        val reopened = gate.observe(300L, left = 0.9f, right = 0.9f)

        assertThat(reopened.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(reopened.isReadyToCapture).isFalse()
    }

    @Test
    fun `wink does not satisfy the both eyes closed requirement`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.9f, right = 0.9f)
        gate.observe(100L, left = 0.9f, right = 0.9f)
        gate.observe(200L, left = 0.1f, right = 0.9f)
        val result = gate.observe(300L, left = 0.1f, right = 0.9f)

        assertThat(result.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(result.progress).isEqualTo(0.3f)
    }

    @Test
    fun `changed tracking id restarts the complete sequence`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.9f, right = 0.9f, trackingId = 11)
        gate.observe(100L, left = 0.9f, right = 0.9f, trackingId = 11)
        gate.observe(200L, left = 0.1f, right = 0.1f, trackingId = 11)
        val changedFace = gate.observe(
            timestampMillis = 300L,
            left = 0.1f,
            right = 0.1f,
            trackingId = 22,
        )

        assertThat(changedFace.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(changedFace.progress).isEqualTo(0f)
    }

    @Test
    fun `invalid position clears previous blink progress`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.9f, right = 0.9f)
        gate.observe(100L, left = 0.9f, right = 0.9f)
        val rejected = gate.observe(
            timestampMillis = 200L,
            left = 0.1f,
            right = 0.1f,
            positionQualified = false,
        )
        val next = gate.observe(300L, left = 0.1f, right = 0.1f)

        assertThat(rejected.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(next.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
    }

    @Test
    fun `closed face timeout requires a new sequence`() {
        val gate = FaceBlinkGate(maximumClosedDurationMillis = 1_000L)

        gate.observe(0L, left = 0.9f, right = 0.9f)
        gate.observe(100L, left = 0.9f, right = 0.9f)
        gate.observe(200L, left = 0.1f, right = 0.1f)
        gate.observe(300L, left = 0.1f, right = 0.1f)
        val timedOut = gate.observe(1_401L, left = 0.9f, right = 0.9f)

        assertThat(timedOut.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(timedOut.isReadyToCapture).isFalse()
    }

    private fun FaceBlinkGate.observe(
        timestampMillis: Long,
        left: Float,
        right: Float,
        trackingId: Int? = 11,
        positionQualified: Boolean = true,
    ): FaceBlinkResult = evaluate(
        FaceBlinkObservation(
            trackingId = trackingId,
            leftEyeOpenProbability = left,
            rightEyeOpenProbability = right,
            positionQualified = positionQualified,
            timestampMillis = timestampMillis,
        ),
    )
}
