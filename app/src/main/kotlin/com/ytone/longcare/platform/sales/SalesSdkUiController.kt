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
    @Volatile
    private var sdkPageActive = false

    fun requiredRuntimePermissions(): Array<String> = qlzSdkClient.requiredRuntimePermissions()

    fun openEvaluation(
        activity: Activity,
        token: String,
        onEvent: (QlzSdkEvent) -> Unit,
    ): SalesSdkOpenResult {
        if (activity.isFinishing || activity.isDestroyed) {
            return SalesSdkOpenResult.InvalidActivity
        }
        synchronized(this) {
            if (sdkPageActive) {
                return SalesSdkOpenResult.AlreadyOpen
            }
            sdkPageActive = true
        }

        return try {
            qlzSdkClient.openByToken(activity, token) { event ->
                if (event !is QlzSdkEvent.Progress) {
                    synchronized(this) {
                        sdkPageActive = false
                    }
                }
                onEvent(event)
            }
            SalesSdkOpenResult.Opened
        } catch (_: Throwable) {
            synchronized(this) {
                sdkPageActive = false
            }
            SalesSdkOpenResult.Failed
        }
    }
}

internal sealed interface SalesSdkOpenResult {
    data object Opened : SalesSdkOpenResult
    data object AlreadyOpen : SalesSdkOpenResult
    data object InvalidActivity : SalesSdkOpenResult
    data object Failed : SalesSdkOpenResult
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
