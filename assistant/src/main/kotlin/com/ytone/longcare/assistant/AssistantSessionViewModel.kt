package com.ytone.longcare.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@androidx.annotation.Keep
@Serializable
enum class AssistantTool(val requiresLogin: Boolean) {
    DEFAULT_FACE(true), NFC(false), CAMERA(false), TENCENT_FACE(true), MANUAL_FACE(false),
}

@Serializable data object AssistantHome
@Serializable data object AssistantLogin
@Serializable data class AssistantToolRoute(val tool: AssistantTool, val orderId: Long = 0)

@HiltViewModel
class AssistantSessionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: UserSessionRepository,
    private val invalidationHandler: SessionInvalidationHandler,
    private val photoCleaner: AssistantPhotoCleaner,
) : ViewModel() {
    val session = repository.sessionState
    val invalidation = invalidationHandler.invalidations
    val result = savedState.getStateFlow("result", "")
    val photoUri = savedState.getStateFlow("photoUri", "")

    fun requireLogin(route: AssistantToolRoute) {
        savedState["pendingTool"] = route.tool.name
        savedState["pendingOrderId"] = route.orderId
    }

    fun takePending(): AssistantToolRoute? {
        val tool = savedState.get<String>("pendingTool")
            ?.let { name -> AssistantTool.entries.firstOrNull { it.name == name } }
        val orderId = savedState.get<Long>("pendingOrderId") ?: 0L
        cancelPending()
        return tool?.let { AssistantToolRoute(it, orderId) }
    }

    fun cancelPending() {
        savedState.remove<String>("pendingTool")
        savedState.remove<Long>("pendingOrderId")
    }

    fun report(message: String) { savedState["result"] = message }
    fun showPhoto(uri: String) {
        val previous = photoUri.value
        if (previous == uri) return
        savedState["photoUri"] = uri
        photoCleaner.discard(previous)
    }
    fun clearResult() { report(""); showPhoto("") }
    fun consumeInvalidation(id: Long) { invalidationHandler.consume(id) }
    fun logout() {
        cancelPending()
        clearResult()
        viewModelScope.launch { repository.logout() }
    }
}
