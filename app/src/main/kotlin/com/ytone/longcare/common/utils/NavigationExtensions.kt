package com.ytone.longcare.common.utils

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator

/**
 * 安全的导航扩展函数
 * 通过检查 Lifecycle 状态来防止重复导航（Double Navigation）
 */

/**
 * 安全的 popBackStack
 * 仅当当前 Lifecycle 为 RESUMED 状态时才执行 pop
 */
fun NavController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

/**
 * 安全的 popBackStack (带参数)
 */
fun <T : Any> NavController.safePopBackStack(route: T, inclusive: Boolean, saveState: Boolean = false) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack(route, inclusive, saveState)
    }
}

/**
 * 安全的 navigate (Route String)
 */
fun NavController.safeNavigate(route: String, builder: (androidx.navigation.NavOptionsBuilder.() -> Unit)? = null) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        if (builder != null) {
            navigate(route, builder)
        } else {
            navigate(route)
        }
    }
}

/**
 * 安全的 navigate (Route Object for Type Safe Navigation)
 */
fun <T : Any> NavController.safeNavigate(route: T, builder: (androidx.navigation.NavOptionsBuilder.() -> Unit)? = null) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        if (builder != null) {
            navigate(route, builder)
        } else {
            navigate(route)
        }
    }
}
