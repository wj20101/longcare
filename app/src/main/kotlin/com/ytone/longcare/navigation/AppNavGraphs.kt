package com.ytone.longcare.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

internal fun NavGraphBuilder.registerAppNavGraphs(navController: NavController) {
    registerEntryNavGraphs(navController)
    registerServiceFlowNavGraphs(navController)
    registerSupportNavGraphs(navController)
}
