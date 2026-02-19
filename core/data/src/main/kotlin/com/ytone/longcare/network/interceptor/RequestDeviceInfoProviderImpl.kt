package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.utils.DeviceUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestDeviceInfoProviderImpl @Inject constructor(
    private val deviceUtils: DeviceUtils,
) : RequestDeviceInfoProvider {
    override fun getAppVersionCode(): Long = deviceUtils.getAppVersionCode()

    override fun getAppVersionName(): String = deviceUtils.getAppVersionName()

    override fun getAppInstanceId(): String = deviceUtils.getAppInstanceId()
}
