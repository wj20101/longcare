package com.ytone.longcare.core.ui.message

import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMessageQueueTest {
    @Test
    fun `messages remain queued until their matching ids are consumed`() {
        val queue = UiMessageQueue()
        val firstId = queue.enqueue("first")
        val secondId = queue.enqueue("second")

        queue.consume(secondId)
        assertEquals(listOf("first"), queue.messages.value.map(UiMessage::text))

        queue.consume(firstId)
        assertTrue(queue.messages.value.isEmpty())
    }

    @Test
    fun `concurrent producers do not overwrite user messages`() {
        val queue = UiMessageQueue()
        val producers = (1..40).map { index ->
            thread(start = true) { queue.enqueue("message-$index") }
        }

        producers.forEach(Thread::join)

        assertEquals(40, queue.messages.value.size)
        assertEquals(40, queue.messages.value.map(UiMessage::id).distinct().size)
    }
}
