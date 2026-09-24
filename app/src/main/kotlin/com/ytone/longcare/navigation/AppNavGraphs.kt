package com.ytone.longcare.navigation


internal fun AppEntryProviderBuilder.registerAppNavGraphs(navController: AppNavigator) {
    registerEntryNavGraphs(navController)
    registerSalesNavGraphs(navController)
    registerServiceFlowNavGraphs(navController)
    registerSupportNavGraphs(navController)
}
