package com.ytone.longcare.features.identification.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationUiActionQueueTest {
    @Test
    fun `actions remain queued in insertion order until consumed`() {
        val queue = IdentificationUiActionQueue()

        queue.enqueue(IdentificationUiEffect.ShowMessage("first"))
        queue.enqueue(IdentificationUiEffect.NavigateToFaceCapture(2))

        val queued = queue.actions.value
        assertEquals(
            listOf("first", 2),
            queued.map { it.effect.payload },
        )

        queue.consume(queued.first().id)

        assertEquals(listOf(queued.last()), queue.actions.value)
    }

    @Test
    fun `consuming an unknown id keeps queued actions`() {
        val queue = IdentificationUiActionQueue()
        queue.enqueue(IdentificationUiEffect.ShowMessage("message"))

        queue.consume(Long.MAX_VALUE)

        assertTrue(queue.actions.value.isNotEmpty())
    }

    private val IdentificationUiEffect.payload: Any
        get() = when (this) {
            is IdentificationUiEffect.NavigateToFaceCapture -> messageRes
            is IdentificationUiEffect.ShowMessage -> message
        }
}
