package com.ytone.longcare.features.location.manager

import android.content.Context
import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.model.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** SDK 初始化只有这一处；每次创建独立客户端，不保存业务位置。 */
open class AmapLocationClientFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val privacyConsent: PrivacyConsentManager,
) {
    open fun create(apiKey: String): AmapClientHandle {
        check(privacyConsent.isPrivacyConsented) { "location_privacy_not_consented" }
        AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTPS)
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
}

class SdkAmapClientHandle(private val client: AMapLocationClient) : AmapClientHandle {
    override fun setLocationOption(option: AMapLocationClientOption) = client.setLocationOption(option)
    override fun setLocationListener(listener: AMapLocationListener) = client.setLocationListener(listener)
    override fun unRegisterLocationListener(listener: AMapLocationListener) = client.unRegisterLocationListener(listener)
    override fun startLocation() = client.startLocation()
    override fun stopLocation() = client.stopLocation()
    override fun onDestroy() = client.onDestroy()
}

internal fun newAmapLocationOption() = AMapLocationClientOption().apply {
    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
    isNeedAddress = false
    isWifiScan = true
    isMockEnable = false
    isLocationCacheEnable = false
    isOffset = true
}

internal fun AMapLocation.toSample(requestId: String) = LocationResult(
    latitude = latitude,
    longitude = longitude,
    provider = provider.orEmpty(),
    accuracy = accuracy,
    coordType = coordType.orEmpty(),
    locationType = locationType,
    trustedLevel = trustedLevel,
    locationTime = time,
    receivedAt = System.currentTimeMillis(),
    receivedElapsedRealtime = SystemClock.elapsedRealtime(),
    isMock = isMock,
    isLastLocation = isFixLastLocation,
    errorCode = errorCode,
    errorInfo = errorInfo.orEmpty(),
    requestId = requestId,
)

internal fun locationFailure(code: Int?): LocationFailure = when (code) {
    AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION,
    AMapLocation.ERROR_CODE_FAILURE_COARSE_LOCATION -> LocationFailure.PERMISSION
    AMapLocation.ERROR_CODE_FAILURE_AUTH,
    AMapLocation.ERROR_CODE_FAILURE_INIT,
    AMapLocation.ERROR_CODE_SERVICE_FAIL,
    AMapLocation.ERROR_CODE_INVALID_PARAMETER -> LocationFailure.CONFIGURATION
    AMapLocation.ERROR_CODE_FAILURE_CONNECTION -> LocationFailure.NETWORK
    AMapLocation.ERROR_CODE_FAILURE_SIMULATION_LOCATION,
    AMapLocation.ERROR_CODE_FAILURE_WIFI_INFO -> LocationFailure.QUALITY
    else -> LocationFailure.UNAVAILABLE
}
