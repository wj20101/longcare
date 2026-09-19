package com.ytone.longcare.navigation


internal fun AppEntryProviderBuilder.registerAppNavGraphs(navController: AppNavigator) {
    registerEntryNavGraphs(navController)
    registerServiceFlowNavGraphs(navController)
    registerSupportNavGraphs(navController)
}
