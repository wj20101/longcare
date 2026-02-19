package com.ytone.longcare.network.interceptor

/**
 * 请求头中设备信息提供能力抽象。
 */
interface RequestDeviceInfoProvider {
    fun getAppVersionCode(): Long
    fun getAppVersionName(): String
    fun getAppInstanceId(): String
}
