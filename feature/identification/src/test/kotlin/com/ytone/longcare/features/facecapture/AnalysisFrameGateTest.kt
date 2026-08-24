package com.ytone.longcare.features.facecapture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalysisFrameGateTest {
    @Test
    fun `frames inside minimum interval are dropped`() {
        val gate = AnalysisFrameGate(minimumFrameIntervalNanos = 50_000_000L)

        assertThat(gate.shouldAnalyze(1_000_000_000L)).isTrue()
        assertThat(gate.shouldAnalyze(1_030_000_000L)).isFalse()
        assertThat(gate.shouldAnalyze(1_050_000_000L)).isTrue()
    }

    @Test
    fun `camera timestamp reset starts a new analysis sequence`() {
        val gate = AnalysisFrameGate(minimumFrameIntervalNanos = 50_000_000L)

        assertThat(gate.shouldAnalyze(2_000_000_000L)).isTrue()
        assertThat(gate.shouldAnalyze(10_000_000L)).isTrue()
        assertThat(gate.shouldAnalyze(30_000_000L)).isFalse()
    }

    @Test
    fun `thirty fps input is evenly reduced to about twenty fps`() {
        val gate = AnalysisFrameGate(minimumFrameIntervalNanos = 50_000_000L)

        val analyzedFrames = (0 until 30).count { frameIndex ->
            gate.shouldAnalyze(frameIndex * 33_333_333L)
        }

        assertThat(analyzedFrames).isEqualTo(20)
    }
}
