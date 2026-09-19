package com.ytone.longcare.integration.qlz

/** Presentation only: never starts/stops the vendor measurement. Clock is monotonic. */
internal class QlzGripPreparation {
    private var deadline: Long? = null
    private var measured = false

    fun update(state: QlzEvaluationUiState, foreground: Boolean, nowMillis: Long): QlzEvaluationUiState {
        val measuring = state.stage == QlzEvaluationStage.CONNECTED ||
            state.stage == QlzEvaluationStage.MEASURING
        if (!measuring && state.stage != QlzEvaluationStage.POWER_PAUSED &&
            state.stage != QlzEvaluationStage.UPLOADING && state.stage != QlzEvaluationStage.ERROR
        ) measured = false
        if (state.totalCount > 0) measured = true
        if (!foreground || !measuring || state.issue != null || state.powerConnected || measured ||
            !state.fingerContacts.allConnected
        ) {
            deadline = null
            return state.copy(preparationSeconds = null, showMeasurementProgress = measured)
        }
        val end = deadline ?: (nowMillis + 5_000L).also { deadline = it }
        val remaining = ((end - nowMillis).coerceAtLeast(0) + 999) / 1_000
        return state.copy(
            preparationSeconds = remaining.toInt().takeIf { it > 0 },
            showMeasurementProgress = remaining == 0L,
        )
    }

    fun reset() {
        deadline = null
        measured = false
    }
}
