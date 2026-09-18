package com.ytone.longcare.assistant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface AssistantRoute : NavKey

@Serializable
data class AssistantEntry(val route: AssistantRoute, val id: String = UUID.randomUUID().toString()) : NavKey

internal class AssistantNavigator(private val stack: MutableList<NavKey>, private val sourceId: String? = null) {
    fun forEntry(id: String) = AssistantNavigator(stack, id)
    private fun active() = sourceId == null || (stack.lastOrNull() as? AssistantEntry)?.id == sourceId
    fun ifCurrent(action: () -> Unit) { if (active()) action() }
    fun navigate(route: AssistantRoute) {
        if (active()) stack.add(AssistantEntry(route))
    }
    fun back() {
        if (active() && stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun home() {
        if (!active()) return
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    fun login() {
        if (!active()) return
        home()
        stack.add(AssistantEntry(AssistantLogin))
    }
    fun reset() {
        if (!active()) return
        stack.clear()
        stack.add(AssistantEntry(AssistantHome))
    }
    fun resume(target: AssistantToolRoute?) {
        if (!active()) return
        home()
        if (target != null) stack.add(AssistantEntry(target))
    }
}
