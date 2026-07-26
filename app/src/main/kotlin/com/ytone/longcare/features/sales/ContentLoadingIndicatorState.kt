package com.ytone.longcare.features.sales

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Compose equivalent of AndroidX ContentLoadingProgressBar timing.
 *
 * Fast work finishes without showing an indicator. Once shown, the indicator
 * remains visible long enough to be perceived instead of flashing.
 */
@Stable
internal class ContentLoadingIndicatorState(
    private val showDelayMillis: Long = SHOW_DELAY_MILLIS,
    private val minimumShowMillis: Long = MINIMUM_SHOW_MILLIS,
    private val nowMillis: () -> Long = SystemClock::uptimeMillis,
) {
    var isVisible by mutableStateOf(false)
        private set

    private var shownAtMillis = 0L

    suspend fun update(isLoading: Boolean) {
        if (isLoading) {
            show()
        } else {
            hide()
        }
    }

    private suspend fun show() {
        if (isVisible) return
        delay(showDelayMillis)
        shownAtMillis = nowMillis()
        isVisible = true
    }

    private suspend fun hide() {
        if (!isVisible) return
        val visibleDuration = nowMillis() - shownAtMillis
        val remainingDuration =
            (minimumShowMillis - visibleDuration).coerceAtLeast(0L)
        delay(remainingDuration)
        isVisible = false
    }

    private companion object {
        const val SHOW_DELAY_MILLIS = 500L
        const val MINIMUM_SHOW_MILLIS = 500L
    }
}

@Composable
internal fun rememberContentLoadingIndicatorState(
    isLoading: Boolean,
): ContentLoadingIndicatorState {
    val state = remember { ContentLoadingIndicatorState() }
    LaunchedEffect(isLoading) {
        state.update(isLoading)
    }
    return state
}
