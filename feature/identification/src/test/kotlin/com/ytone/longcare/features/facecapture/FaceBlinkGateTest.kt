package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceBlinkGateTest {
    @Test
    fun `stable open closed reopen sequence completes blink verification`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.90f, right = 0.91f)
        gate.observe(100L, left = 0.91f, right = 0.90f)
        assertThat(gate.observe(200L, left = 0.89f, right = 0.90f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(300L, left = 0.22f, right = 0.21f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(400L, left = 0.21f, right = 0.22f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_REOPEN)
        val reopening = gate.observe(500L, left = 0.88f, right = 0.89f)
        assertThat(reopening.stage)
            .isEqualTo(FaceBlinkStage.VERIFYING_REOPEN)
        assertThat(reopening.progress).isEqualTo(0.25f)
        assertThat(gate.observe(650L, left = 0.89f, right = 0.88f).isReadyToCapture)
            .isFalse()

        val completed = gate.observe(800L, left = 0.90f, right = 0.91f)

        assertThat(completed.stage).isEqualTo(FaceBlinkStage.COMPLETE)
        assertThat(completed.progress).isEqualTo(1f)
        assertThat(completed.isReadyToCapture).isTrue()
    }

    @Test
    fun `naturally lower open eye scores use their own baseline`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.48f, right = 0.45f)
        gate.observe(100L, left = 0.50f, right = 0.47f)
        assertThat(gate.observe(200L, left = 0.46f, right = 0.44f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(300L, left = 0.10f, right = 0.09f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(350L, left = 0.09f, right = 0.10f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_REOPEN)
        gate.observe(400L, left = 0.32f, right = 0.30f)
        gate.observe(550L, left = 0.33f, right = 0.31f)

        assertThat(gate.observe(700L, left = 0.32f, right = 0.30f).isReadyToCapture)
            .isTrue()
    }

    @Test
    fun `one strongly closed frame is noise while two consecutive frames confirm a blink`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()

        assertThat(gate.observe(300L, left = 0.08f, right = 0.09f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(gate.observe(350L, left = 0.09f, right = 0.08f).stage)
            .isEqualTo(FaceBlinkStage.WAITING_FOR_REOPEN)
        gate.observe(400L, left = 0.89f, right = 0.90f)
        gate.observe(550L, left = 0.90f, right = 0.89f)

        assertThat(gate.observe(700L, left = 0.91f, right = 0.90f).isReadyToCapture)
            .isTrue()
    }

    @Test
    fun `one moderate closed frame is treated as noise`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()

        val noisyClosedFrame = gate.observe(300L, left = 0.22f, right = 0.22f)
        val reopened = gate.observe(400L, left = 0.90f, right = 0.90f)

        assertThat(noisyClosedFrame.progress).isEqualTo(0f)
        assertThat(reopened.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(reopened.isReadyToCapture).isFalse()
    }

    @Test
    fun `wink does not satisfy the both eyes closed requirement`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()

        gate.observe(300L, left = 0.08f, right = 0.90f)
        val result = gate.observe(400L, left = 0.07f, right = 0.89f)

        assertThat(result.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
        assertThat(result.progress).isEqualTo(0f)
    }

    @Test
    fun `short reopen probability gaps do not restart the blink flow`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()
        gate.observe(300L, left = 0.20f, right = 0.19f)
        gate.observe(350L, left = 0.19f, right = 0.20f)

        val firstReopen = gate.observe(400L, left = 0.90f, right = 0.89f)
        val firstGap = gate.observe(450L, left = 0.30f, right = 0.90f)
        val secondReopen = gate.observe(500L, left = 0.89f, right = 0.90f)
        val secondGap = gate.observe(550L, left = 0.90f, right = 0.30f)
        val completed = gate.observe(650L, left = 0.91f, right = 0.90f)

        assertThat(firstReopen.stage).isEqualTo(FaceBlinkStage.VERIFYING_REOPEN)
        assertThat(firstGap.stage).isEqualTo(FaceBlinkStage.VERIFYING_REOPEN)
        assertThat(secondGap.stage).isEqualTo(FaceBlinkStage.VERIFYING_REOPEN)
        assertThat(firstGap.progress).isAtLeast(firstReopen.progress)
        assertThat(secondGap.progress).isAtLeast(secondReopen.progress)
        assertThat(completed.isReadyToCapture).isTrue()
    }

    @Test
    fun `persistent reopen failure waits for new reopen evidence without requesting another blink`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()
        gate.observe(300L, left = 0.20f, right = 0.19f)
        gate.observe(350L, left = 0.19f, right = 0.20f)
        gate.observe(400L, left = 0.90f, right = 0.89f)

        gate.observe(450L, left = 0.20f, right = 0.20f)
        gate.observe(500L, left = 0.20f, right = 0.20f)
        val waitingForReopen = gate.observe(550L, left = 0.20f, right = 0.20f)

        assertThat(waitingForReopen.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_REOPEN)
        assertThat(waitingForReopen.isReadyToCapture).isFalse()
    }

    @Test
    fun `eye scores below confidence floor never arm blink detection`() {
        val gate = FaceBlinkGate()

        repeat(6) { index ->
            gate.observe(index * 100L, left = 0.30f, right = 0.31f)
        }

        val result = gate.observe(700L, left = 0.05f, right = 0.04f)
        assertThat(result.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(result.isReadyToCapture).isFalse()
    }

    @Test
    fun `unstable baseline samples must settle before blink detection arms`() {
        val gate = FaceBlinkGate()

        gate.observe(0L, left = 0.40f, right = 0.42f)
        gate.observe(100L, left = 0.80f, right = 0.82f)
        val unstable = gate.observe(200L, left = 0.41f, right = 0.43f)

        assertThat(unstable.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(unstable.isReadyToCapture).isFalse()

        gate.observe(300L, left = 0.42f, right = 0.44f)
        val settled = gate.observe(400L, left = 0.40f, right = 0.42f)
        assertThat(settled.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_BLINK)
    }

    @Test
    fun `invalid position clears previous blink progress`() {
        val gate = FaceBlinkGate()
        gate.establishBaseline()

        val rejected = gate.observe(
            timestampMillis = 300L,
            left = 0.08f,
            right = 0.08f,
            positionQualified = false,
        )
        val next = gate.observe(400L, left = 0.08f, right = 0.08f)

        assertThat(rejected.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(next.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
    }

    @Test
    fun `closed face timeout requires a new baseline and sequence`() {
        val gate = FaceBlinkGate(maximumClosedDurationMillis = 1_000L)
        gate.establishBaseline()
        gate.observe(300L, left = 0.08f, right = 0.08f)
        gate.observe(350L, left = 0.08f, right = 0.08f)

        val timedOut = gate.observe(1_401L, left = 0.90f, right = 0.90f)

        assertThat(timedOut.stage).isEqualTo(FaceBlinkStage.WAITING_FOR_OPEN_EYES)
        assertThat(timedOut.isReadyToCapture).isFalse()
    }

    private fun FaceBlinkGate.establishBaseline() {
        observe(0L, left = 0.90f, right = 0.90f)
        observe(100L, left = 0.91f, right = 0.89f)
        observe(200L, left = 0.89f, right = 0.91f)
    }

    private fun FaceBlinkGate.observe(
        timestampMillis: Long,
        left: Float,
        right: Float,
        positionQualified: Boolean = true,
    ): FaceBlinkResult = evaluate(
        FaceBlinkObservation(
            leftEyeOpenProbability = left,
            rightEyeOpenProbability = right,
            positionQualified = positionQualified,
            timestampMillis = timestampMillis,
        ),
    )
}
