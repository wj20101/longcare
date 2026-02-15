package com.ytone.longcare.model

/**
 * 状态:0待执行 1执行中 2任务完成 3作废
 */
fun Int.toStateDisplayText(): String {
    return when (this) {
        -1 -> "未开单"
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

/**
 * 判断是否为未开单状态
 */
fun Int.isNotStartedState(): Boolean {
    val value: Int = this
    return value == -1
}

/**
 * 统一的订单跳转处理函数
 * @param state 订单状态
 * @param orderId 订单ID
 * @param planId 计划ID
 * @param onNavigateToNursingExecution 跳转到护理执行页面的回调
 * @param onNavigateToService 跳转到服务页面的回调
 * @param onNotStartedState 未开单状态的回调（可选）
 */
fun handleOrderNavigation(
    state: Int,
    orderId: Long,
    planId: Int = 0,
    onNavigateToNursingExecution: (Long, Int) -> Unit,
    onNavigateToService: (Long, Int) -> Unit,
    onNotStartedState: (() -> Unit)? = null
) {
    when {
        state.isNotStartedState() -> {
            // 未开单状态，执行回调或默认不处理
            onNotStartedState?.invoke()
        }
        state.isPendingCareState() -> {
            // 待护理计划状态，跳转到护理执行页面
            onNavigateToNursingExecution(orderId, planId)
        }
        else -> {
            // 其他状态，跳转到服务页面
            onNavigateToService(orderId, planId)
        }
    }
}