package com.ytone.longcare.features.location.manager

import android.content.Context
import com.amap.api.location.AMapLocationClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** SDK 初始化只有这一处；每次创建独立客户端，不保存业务位置。 */
class AmapLocationClientFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(apiKey: String): AMapLocationClient {
        AMapLocationClient.setApiKey(apiKey)
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        return AMapLocationClient(context)
    }
}
