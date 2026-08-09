package com.ytone.longcare.platform.sales

import com.ytone.longcare.integration.qlz.QlzSdkClient
import javax.inject.Inject
import javax.inject.Singleton

/** Business-facing access to the evaluation device without exposing SDK UI APIs to ViewModels. */
@Singleton
class SalesEvaluationDeviceGateway @Inject constructor(
    private val qlzSdkClient: QlzSdkClient,
) {
    fun getDeviceId(): Result<String> = qlzSdkClient.getDeviceId()

    fun getConnectedDeviceName(): String? = qlzSdkClient.getConnectedDeviceName()
}
