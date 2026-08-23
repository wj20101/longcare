package com.ytone.longcare.core.ui.message

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiMessage(
    val id: Long,
    val content: UiText,
)

sealed interface UiText {
    data class Dynamic(val value: String) : UiText

    data class Resource(
        @param:StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList(),
    ) : UiText
}

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
        val message = UiMessage(id = ++nextId, content = UiText.Dynamic(text))
        mutableMessages.value = mutableMessages.value + message
        return message.id
    }

    @Synchronized
    fun enqueue(@StringRes resId: Int, vararg formatArgs: Any): Long {
        val message = UiMessage(
            id = ++nextId,
            content = UiText.Resource(resId = resId, formatArgs = formatArgs.toList()),
        )
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
    val resolvedText = nextMessage?.content?.let { content ->
        when (content) {
            is UiText.Dynamic -> content.value
            is UiText.Resource -> stringResource(
                content.resId,
                *content.formatArgs.toTypedArray(),
            )
        }
    }
    LaunchedEffect(nextMessage?.id, resolvedText) {
        val message = nextMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(requireNotNull(resolvedText))
        onConsumed(message.id)
    }
}
