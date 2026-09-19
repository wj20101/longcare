package com.ytone.longcare.common.utils

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/**
 * 包装点击事件，防止快速连续点击
 * @param delayMillis 防抖延迟时间（毫秒），默认500ms
 * @param onClick 点击事件回调
 * @return 包装后的点击事件回调
 */
@Composable
fun singleClick(
    delayMillis: Long = 500L,
    onClick: () -> Unit
): () -> Unit {
    val lastClickTime = remember { mutableLongStateOf(0L) }
    
    return {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime.longValue >= delayMillis) {
            lastClickTime.longValue = currentTime
            onClick()
        }
    }
}
