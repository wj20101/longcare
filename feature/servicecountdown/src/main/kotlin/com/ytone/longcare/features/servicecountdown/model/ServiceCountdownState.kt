package com.ytone.longcare.features.servicecountdown.model

/**
 * 服务倒计时状态
 */
enum class ServiceCountdownState {
    RUNNING,    // 倒计时运行中
    COMPLETED,  // 倒计时完成
    OVERTIME,   // 倒计时超时
    ENDED       // 服务已结束
}
