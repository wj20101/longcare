package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.model.OrderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificationUiActionQueueTest {
    @Test
    fun `actions remain queued in insertion order until consumed`() {
        val queue = IdentificationUiActionQueue()
        val orderKey = OrderKey(orderId = 123L)

        queue.enqueue(IdentificationUiEffect.ShowMessage("first"))
        queue.enqueue(IdentificationUiEffect.NavigateToDefaultFaceVerification(orderKey))
        queue.enqueue(IdentificationUiEffect.NavigateToFaceCapture(2))

        val queued = queue.actions.value
        assertEquals(
            listOf("first", orderKey, 2),
            queued.map { it.effect.payload },
        )

        queue.consume(queued.first().id)

        assertEquals(queued.drop(1), queue.actions.value)
    }

    @Test
    fun `consuming an unknown id keeps queued actions`() {
        val queue = IdentificationUiActionQueue()
        queue.enqueue(IdentificationUiEffect.ShowMessage("message"))

        queue.consume(Long.MAX_VALUE)

        assertTrue(queue.actions.value.isNotEmpty())
    }

    @Test
    fun `actions queued without collectors remain available to a later collector`() {
        val queue = IdentificationUiActionQueue()

        queue.enqueue(IdentificationUiEffect.ShowMessage("while-stopped"))
        queue.enqueue(IdentificationUiEffect.NavigateToFaceCapture(7))

        assertEquals(
            listOf("while-stopped", 7),
            queue.actions.value.map { it.effect.payload },
        )
    }

    @Test
    fun `consuming the same action twice is idempotent and preserves FIFO`() {
        val queue = IdentificationUiActionQueue()
        queue.enqueue(IdentificationUiEffect.ShowMessage("first"))
        queue.enqueue(IdentificationUiEffect.ShowMessage("second"))
        queue.enqueue(IdentificationUiEffect.ShowMessage("third"))
        val firstId = queue.actions.value.first().id

        queue.consume(firstId)
        queue.consume(firstId)

        assertEquals(
            listOf("second", "third"),
            queue.actions.value.map { it.effect.payload },
        )
        assertFalse(queue.actions.value.any { it.id == firstId })
    }

    @Test
    fun `consumed action does not reappear when a later action is enqueued`() {
        val queue = IdentificationUiActionQueue()
        queue.enqueue(IdentificationUiEffect.ShowMessage("consumed"))
        val consumedId = queue.actions.value.single().id
        queue.consume(consumedId)

        queue.enqueue(IdentificationUiEffect.ShowMessage("later"))

        assertEquals(listOf("later"), queue.actions.value.map { it.effect.payload })
        assertFalse(queue.actions.value.any { it.id == consumedId })
    }

    private val IdentificationUiEffect.payload: Any
        get() = when (this) {
            is IdentificationUiEffect.NavigateToDefaultFaceVerification -> orderKey
            is IdentificationUiEffect.NavigateToFaceCapture -> messageRes
            is IdentificationUiEffect.ShowMessage -> message
        }
}
