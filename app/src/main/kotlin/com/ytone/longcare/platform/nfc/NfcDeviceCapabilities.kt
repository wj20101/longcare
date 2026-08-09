package com.ytone.longcare.platform.nfc

import android.content.Context
import com.ytone.longcare.common.utils.NfcUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android-backed NFC capability query with no platform type exposed to ViewModels. */
@Singleton
class NfcDeviceCapabilities @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun isNfcSupported(): Boolean = NfcUtils.isNfcSupported(context)
}
