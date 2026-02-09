package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NfcTestViewModel @Inject constructor(
    private val nfcTestHelper: NfcTestHelper
) : ViewModel() {

    fun enableNfcTest(activity: Activity) {
        nfcTestHelper.enable(activity)
    }

    fun disableNfcTest(activity: Activity) {
        nfcTestHelper.disable(activity)
    }

    fun getHelper(): NfcTestHelper {
        return nfcTestHelper
    }
}
