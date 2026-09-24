package com.ytone.longcare.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.sales.SalesExperienceScreen

internal fun AppEntryProviderBuilder.registerSalesNavGraphs(navigator: AppNavigator) {
    destination<SalesRoute> { entry ->
        val controller = navigator.forEntry(entry.id, LocalLifecycleOwner.current.lifecycle)
        SalesExperienceScreen(
            actions = homeActions(controller, entry),
            homeSharedViewModel = hiltViewModel<HomeSharedViewModel>(LocalHomeViewModelStoreOwner.current),
            navigator = controller,
            route = entry.route(),
        )
    }
}
