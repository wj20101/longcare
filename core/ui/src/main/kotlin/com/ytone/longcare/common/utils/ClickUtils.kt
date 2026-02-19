package com.ytone.longcare.common.utils

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role

/**
 * 防止快速点击（防抖）的工具类和扩展函数
 */

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

/**
 * Modifier扩展：带防抖的点击事件
 * @param delayMillis 防抖延迟时间（毫秒），默认500ms
 * @param enabled 是否启用
 * @param onClickLabel 点击标签
 * @param role 角色
 * @param onClick 点击事件回调
 */
fun Modifier.throttleClick(
    delayMillis: Long = 500L,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "throttleClick"
        properties["delayMillis"] = delayMillis
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    val multipleEventsCutter = remember { MultipleEventsCutter.get(delayMillis) }
    
    Modifier.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = { multipleEventsCutter.processEvent { onClick() } },
        role = role,
        indication = androidx.compose.foundation.LocalIndication.current,
        interactionSource = remember { MutableInteractionSource() }
    )
}

/**
 * 内部使用的事件切割器
 */
private class MultipleEventsCutter private constructor(
    private val delayMillis: Long
) {
    private var lastEventTimeMs: Long = 0

    fun processEvent(event: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEventTimeMs >= delayMillis) {
            event.invoke()
        }
        lastEventTimeMs = now
    }

    companion object {
        fun get(delayMillis: Long): MultipleEventsCutter = MultipleEventsCutter(delayMillis)
    }
}
