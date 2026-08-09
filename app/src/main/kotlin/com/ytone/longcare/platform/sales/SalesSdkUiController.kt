package com.ytone.longcare.platform.sales

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** UI-scoped boundary for QLZ operations that require the current Activity. */
internal class SalesSdkUiController(
    private val qlzSdkClient: QlzSdkClient,
) {
    fun requiredRuntimePermissions(): Array<String> = qlzSdkClient.requiredRuntimePermissions()

    fun openEvaluation(activity: Activity, token: String, onEvent: (QlzSdkEvent) -> Unit) {
        qlzSdkClient.openByToken(activity, token, onEvent)
    }

    fun openReport(activity: Activity, reportUrl: String) {
        qlzSdkClient.openReport(activity, reportUrl)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface SalesSdkUiEntryPoint {
    fun qlzSdkClient(): QlzSdkClient
}

@Composable
internal fun rememberSalesSdkUiController(): SalesSdkUiController {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        SalesSdkUiController(applicationContext.qlzSdkClient())
    }
}

private fun Context.qlzSdkClient(): QlzSdkClient = EntryPointAccessors.fromApplication(
    this,
    SalesSdkUiEntryPoint::class.java,
).qlzSdkClient()
