package com.ytone.longcare.core.ui.message

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiMessage(
    val id: Long,
    val text: String,
)

/**
 * Acknowledged FIFO storage for user-visible messages.
 *
 * Unlike replay-zero event flows, messages survive temporary collector absence and are removed
 * only after the UI acknowledges the matching id.
 */
class UiMessageQueue {
    private val mutableMessages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = mutableMessages.asStateFlow()

    private var nextId = 0L

    @Synchronized
    fun enqueue(text: String): Long {
        val message = UiMessage(id = ++nextId, text = text)
        mutableMessages.value = mutableMessages.value + message
        return message.id
    }

    @Synchronized
    fun consume(id: Long) {
        mutableMessages.value = mutableMessages.value.filterNot { it.id == id }
    }
}

@Composable
fun UiMessageSnackbarEffect(
    messages: List<UiMessage>,
    snackbarHostState: SnackbarHostState,
    onConsumed: (Long) -> Unit,
) {
    val nextMessage = messages.firstOrNull()
    LaunchedEffect(nextMessage?.id) {
        val message = nextMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        onConsumed(message.id)
    }
}
