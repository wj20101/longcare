package com.ytone.longcare.common.config

/**
 * 跨模块运行时配置读取接口。
 * 用于隔离 feature/core 层对 app BuildConfig 的直接依赖。
 */
interface RuntimeConfigProvider {
    val useMockData: Boolean
    val baseUrl: String
    val isDebug: Boolean
    val publicKey: String
}
