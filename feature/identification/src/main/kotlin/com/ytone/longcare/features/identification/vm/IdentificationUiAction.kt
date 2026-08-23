package com.ytone.longcare.features.identification.vm

import androidx.annotation.StringRes
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A UI action stored in ViewModel state until the UI acknowledges it.
 *
 * Unlike a replay-zero event stream, this queue survives temporary collector absence
 * during lifecycle transitions and preserves action ordering.
 */
data class IdentificationUiAction(
    val id: Long,
    val effect: IdentificationUiEffect,
)

sealed interface IdentificationUiEffect {
    data class NavigateToFaceCapture(
        @param:StringRes val messageRes: Int,
    ) : IdentificationUiEffect

    data class ShowMessage(
        val message: String,
        val long: Boolean = false,
    ) : IdentificationUiEffect
}

internal class IdentificationUiActionQueue {
    private val nextId = AtomicLong(0)
    private val _actions = MutableStateFlow<List<IdentificationUiAction>>(emptyList())

    val actions: StateFlow<List<IdentificationUiAction>> = _actions.asStateFlow()

    fun enqueue(effect: IdentificationUiEffect) {
        val action = IdentificationUiAction(
            id = nextId.incrementAndGet(),
            effect = effect,
        )
        _actions.update { actions -> actions + action }
    }

    fun consume(actionId: Long) {
        _actions.update { actions -> actions.filterNot { it.id == actionId } }
    }
}
