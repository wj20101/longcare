package com.ytone.longcare.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey

@Serializable
data class AppNavEntry(
    val route: AppRoute,
    val id: String = UUID.randomUUID().toString(),
    val callerId: String? = null,
    val homeId: String? = null,
) : NavKey

/** A page receives a source-bound view; late callbacks cannot mutate a different page's stack. */
class AppNavigator internal constructor(
    internal val backStack: MutableList<NavKey>,
    internal val results: NavigationResults,
    private val sourceId: String? = null,
    private val lifecycle: Lifecycle? = null,
) {
    internal fun forEntry(id: String, lifecycle: Lifecycle? = null) =
        AppNavigator(backStack, results, id, lifecycle)

    private fun isCurrentSource() = sourceId == null || backStack.lastOrNull()?.let { (it as AppNavEntry).id } == sourceId
    private fun active() = isCurrentSource() &&
        (lifecycle == null || lifecycle.currentState == Lifecycle.State.RESUMED)

    internal fun canHandleCallback(): Boolean = active()

    internal fun handle(entry: AppNavEntry) = AppEntryHandle(entry, results.handle(entry.id, ::isCurrentSource))
    internal val previousBackStackEntry: AppEntryHandle?
        get() {
            val source = backStack.filterIsInstance<AppNavEntry>().find { it.id == sourceId } ?: return null
            return backStack.filterIsInstance<AppNavEntry>().find { it.id == source.callerId }?.let(::handle)
        }

    fun navigate(route: AppRoute) {
        if (!isCurrentSource()) return
        backStack.add(AppNavEntry(route, callerId = (backStack.lastOrNull() as? AppNavEntry)?.id,
            homeId = backStack.filterIsInstance<AppNavEntry>().firstOrNull { it.route == HomeRoute }?.id))
    }

    fun navigateWhenResumed(route: AppRoute) { if (active()) navigate(route) }

    fun <T> returnResult(key: String, value: T) {
        if (!isCurrentSource()) return
        previousBackStackEntry?.results?.set(key, value)
        popBackStack()
    }

    fun popBackStack(): Boolean {
        if (!isCurrentSource() || backStack.size <= 1) return false
        removeFrom(backStack.lastIndex)
        return true
    }

    fun replaceTop(route: AppRoute) {
        if (!isCurrentSource() || backStack.isEmpty()) return
        val caller = (backStack.last() as AppNavEntry).callerId
        val home = (backStack.last() as AppNavEntry).homeId
        removeFrom(backStack.lastIndex)
        backStack.add(AppNavEntry(route, callerId = caller, homeId = home))
    }

    fun resetToHome() {
        if (!isCurrentSource()) return
        removeFrom(0)
        backStack.add(AppNavEntry(HomeRoute))
    }

    fun completeService(route: ServiceCompleteRoute) {
        if (!isCurrentSource()) return
        val home = backStack.indexOfFirst { (it as AppNavEntry).route == HomeRoute }
        if (home < 0) {
            removeFrom(0)
            backStack.add(AppNavEntry(HomeRoute))
        } else removeFrom(home + 1)
        backStack.add(AppNavEntry(route, callerId = (backStack.last() as AppNavEntry).id))
    }

    private fun removeFrom(index: Int) {
        while (backStack.size > index) {
            results.drop((backStack.removeAt(backStack.lastIndex) as AppNavEntry).id)
        }
    }
}

internal class AppEntryHandle(val entry: AppNavEntry, val results: EntryResults) {
    val id get() = entry.id
    inline fun <reified T : AppRoute> route(): T = entry.route as T
}
