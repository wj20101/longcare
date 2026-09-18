package com.ytone.longcare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import kotlin.reflect.KClass

/** The registry dispatches payloads; Navigation 3 owns composition and entry lifetimes. */
internal class AppEntryProviderBuilder {
    val destinations = mutableMapOf<KClass<out AppRoute>, @Composable (AppEntryHandle) -> Unit>()

    inline fun <reified T : AppRoute> destination(noinline content: @Composable (AppEntryHandle) -> Unit) {
        check(destinations.put(T::class, content) == null) { "Duplicate route ${T::class}" }
    }

    @Composable fun Content(entry: AppNavEntry, navigator: AppNavigator) {
        val scoped = navigator.forEntry(entry.id, LocalLifecycleOwner.current.lifecycle)
        checkNotNull(destinations[entry.route::class]) { "Unregistered route ${entry.route}" }(scoped.handle(entry))
    }
}

internal val LocalHomeViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("Home owner is only available in the Home flow")
}

@Composable
internal fun rememberHomeViewModelDecorator(navigator: AppNavigator): NavEntryDecorator<androidx.navigation3.runtime.NavKey> {
    val provider = rememberViewModelStoreProvider(checkNotNull(LocalViewModelStoreOwner.current))
    val context = LocalContext.current
    DisposableEffect(provider, navigator, context) {
        onDispose {
            // A removed NavDisplay (logout/account switch) is not an entry pop. Release its stores,
            // but retain them for an Activity configuration recreation.
            if (context.navigationActivity()?.isChangingConfigurations != true) {
                navigator.backStack.filterIsInstance<AppNavEntry>().forEach { provider.clearKey(it.id) }
            }
        }
    }
    return remember(provider, navigator) {
        NavEntryDecorator(
            onPop = { key ->
                provider.clearKey(key)
                // The outgoing animation can briefly re-read its mailbox after the logical pop.
                navigator.results.drop(key as String)
            },
            decorate = { navEntry ->
                val own = rememberViewModelStoreOwner(navEntry.contentKey, provider,
                    savedStateRegistryOwner = LocalSavedStateRegistryOwner.current)
                val homeId = navEntry.metadata["homeOwnerId"] as? String
                val shared = when (homeId) {
                    null -> null
                    navEntry.contentKey -> own
                    else -> rememberViewModelStoreOwner(homeId, provider,
                        savedStateRegistryOwner = LocalSavedStateRegistryOwner.current)
                }
                CompositionLocalProvider(LocalViewModelStoreOwner provides own) {
                    if (shared != null) CompositionLocalProvider(LocalHomeViewModelStoreOwner provides shared) { navEntry.Content() }
                    else navEntry.Content()
                }
            },
        )
    }
}

private tailrec fun android.content.Context.navigationActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.navigationActivity()
    else -> null
}
