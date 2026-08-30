package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.registerAppNavGraphs(
    navController: NavController,
    onLoginSuccess: () -> Unit = {
        reconcileAuthenticationRoot(navController, AuthenticationRoot.Home)
        Unit
    },
    entryDestinationRenderers: EntryDestinationRenderers = productionEntryDestinationRenderers,
) {
    registerEntryNavGraphs(
        navController = navController,
        onLoginSuccess = onLoginSuccess,
        entryDestinationRenderers = entryDestinationRenderers,
    )
    registerServiceFlowNavGraphs(navController)
    registerSupportNavGraphs(navController)
}
