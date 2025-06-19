package com.ytone.longcare.model

/**
 * 状态:0待执行 1执行中 2任务完成 3作废
 */
fun Int.toStateDisplayText(): String {
    return when (this) {
        0 -> "待执行"
        1 -> "执行中"
        2 -> "任务完成"
        3 -> "作废"
        else -> "其它"
    }
}

/**
 * 判断是否为待护理计划状态（待执行或执行中）
 */
fun Int.isPendingCareState(): Boolean {
    val value: Int = this
    return value == 0 || value == 1
}

/**
 * 判断是否为服务记录状态（任务完成）
 */
fun Int.isServiceRecordState(): Boolean {
    val value: Int = this
    return value == 2
}

/**
 * 判断是否为作废状态
 */
fun Int.isCancelledState(): Boolean {
    val value: Int = this
    return value == 3
}

/**
 * 判断是否为待执行状态
 */
fun Int.isPendingExecutionState(): Boolean {
    val value: Int = this
    return value == 0
}

/**
 * 判断是否为执行中状态
 */
fun Int.isExecutingState(): Boolean {
    val value: Int = this
    return value == 1
}