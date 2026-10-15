package com.ytone.longcare.features.location.manager

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import android.app.Notification
import com.ytone.longcare.common.utils.PrivacyConsentManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** SDK 初始化只有这一处；每次创建独立客户端，不保存业务位置。 */
open class AmapLocationClientFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val privacyConsent: PrivacyConsentManager,
) {
    open fun create(apiKey: String): AmapClientHandle {
        check(privacyConsent.isPrivacyConsented) { "location_privacy_not_consented" }
        AMapLocationClient.setApiKey(apiKey)
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        return SdkAmapClientHandle(AMapLocationClient(context))
    }
}

/** Narrow SDK boundary, allowing device lifecycle tests to use an isolated location source. */
interface AmapClientHandle {
    fun setLocationOption(option: AMapLocationClientOption)
    fun setLocationListener(listener: AMapLocationListener)
    fun unRegisterLocationListener(listener: AMapLocationListener)
    fun startLocation()
    fun stopLocation()
    fun onDestroy()
    fun enableBackgroundLocation(id: Int, notification: Notification)
    fun disableBackgroundLocation(removeNotification: Boolean)
}

class SdkAmapClientHandle(private val client: AMapLocationClient) : AmapClientHandle {
    override fun setLocationOption(option: AMapLocationClientOption) = client.setLocationOption(option)
    override fun setLocationListener(listener: AMapLocationListener) = client.setLocationListener(listener)
    override fun unRegisterLocationListener(listener: AMapLocationListener) = client.unRegisterLocationListener(listener)
    override fun startLocation() = client.startLocation()
    override fun stopLocation() = client.stopLocation()
    override fun onDestroy() = client.onDestroy()
    override fun enableBackgroundLocation(id: Int, notification: Notification) = client.enableBackgroundLocation(id, notification)
    override fun disableBackgroundLocation(removeNotification: Boolean) = client.disableBackgroundLocation(removeNotification)
}
