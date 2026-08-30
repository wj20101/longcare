package com.ytone.longcare.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

internal fun NavController.requireHomeGraphBackStackEntry(): NavBackStackEntry = try {
    getBackStackEntry(HomeGraphRoute)
} catch (error: IllegalArgumentException) {
    throw IllegalStateException(
        "HomeGraphRoute must be present before resolving its shared ViewModel owner.",
        error,
    )
}
