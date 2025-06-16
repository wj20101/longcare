package com.ytone.longcare.model

/**
 * 状态:0待执行 1执行中 2任务完成 3作废
 */
fun Int.stateShow(): String {
    return when (this) {
        0 -> "待执行"
        1 -> "执行中"
        2 -> "任务完成"
        3 -> "作废"
        else -> "其它"
    }
}