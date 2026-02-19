package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * 一个可组合函数，用于在进入组合时锁定屏幕方向，并在退出时恢复。
 * @param orientation 要锁定的方向，来自 [ActivityInfo]，例如 [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]。
 */
@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose {
            // 当 Composable 离开组合（例如导航到其他页面）时，恢复原始的屏幕方向。
            activity.requestedOrientation = originalOrientation
        }
    }
}