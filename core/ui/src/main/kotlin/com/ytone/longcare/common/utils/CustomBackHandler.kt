package com.ytone.longcare.common.utils

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 仅处理自定义返回动作的简化返回键处理器。
 */
@Composable
fun CustomBackHandler(
    customAction: () -> Unit,
    enabled: Boolean = true,
) {
    BackHandler(enabled = enabled) {
        customAction()
    }
}
