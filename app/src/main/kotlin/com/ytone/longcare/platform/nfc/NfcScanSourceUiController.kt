package com.ytone.longcare.platform.nfc

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.common.utils.ExternalRfidReaderManager
import com.ytone.longcare.common.utils.NfcManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** UI-owned entry point for scan operations whose lifecycle is tied to an Activity. */
class NfcScanSourceUiController internal constructor(
    private val nfcManager: NfcManager,
    private val externalRfidReaderManager: ExternalRfidReaderManager,
) {
    fun startSystemNfc(activity: Activity) = nfcManager.enableNfcForActivity(activity)

    fun stopSystemNfc(activity: Activity) = nfcManager.disableNfcForActivity(activity)

    fun startExternalReader(activity: Activity) = externalRfidReaderManager.start(activity)

    fun stopExternalReader(activity: Activity) = externalRfidReaderManager.stop(activity)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface NfcScanSourceUiEntryPoint {
    fun nfcManager(): NfcManager

    fun externalRfidReaderManager(): ExternalRfidReaderManager
}

@Composable
fun rememberNfcScanSourceUiController(): NfcScanSourceUiController {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        val entryPoint = applicationContext.nfcScanSourceUiEntryPoint()
        NfcScanSourceUiController(
            nfcManager = entryPoint.nfcManager(),
            externalRfidReaderManager = entryPoint.externalRfidReaderManager(),
        )
    }
}

private fun Context.nfcScanSourceUiEntryPoint(): NfcScanSourceUiEntryPoint =
    EntryPointAccessors.fromApplication(this, NfcScanSourceUiEntryPoint::class.java)
