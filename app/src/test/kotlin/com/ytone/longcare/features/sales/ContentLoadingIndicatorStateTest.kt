package com.ytone.longcare.features.sales

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContentLoadingIndicatorStateTest {

    @Test
    fun `fast loading never shows the indicator`() = runTest {
        val state =
            ContentLoadingIndicatorState(
                nowMillis = { testScheduler.currentTime },
            )
        val showJob = launch { state.update(isLoading = true) }

        advanceTimeBy(200)
        showJob.cancelAndJoin()
        state.update(isLoading = false)

        assertFalse(state.isVisible)
    }

    @Test
    fun `slow loading is delayed and then kept visible for minimum time`() =
        runTest {
            val state =
                ContentLoadingIndicatorState(
                    nowMillis = { testScheduler.currentTime },
                )
            val showJob = launch { state.update(isLoading = true) }

            advanceTimeBy(499)
            runCurrent()
            assertFalse(state.isVisible)

            advanceTimeBy(1)
            runCurrent()
            assertTrue(state.isVisible)
            showJob.join()

            val hideJob = launch { state.update(isLoading = false) }
            advanceTimeBy(499)
            runCurrent()
            assertTrue(state.isVisible)

            advanceTimeBy(1)
            runCurrent()
            assertFalse(state.isVisible)
            hideJob.join()
        }
}
