package com.ytone.longcare.core.common.service

import android.app.ActivityManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import javax.inject.Inject

/** Confirms this Service's promotion once, not process importance or notification visibility. */
class ForegroundServiceStateVerifier @Inject constructor() {
    fun isForeground(service: Service, requiredType: Int): Boolean =
        if (requiredType == 0) {
            false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.foregroundServiceType and requiredType == requiredType
        } else {
            isLegacyServiceForeground(service)
        }

    @Suppress("DEPRECATION")
    private fun isLegacyServiceForeground(service: Service): Boolean {
        // API 24–28 has no Service foreground-type getter. The deprecated API still exposes
        // the caller's own services; use it only for this one-shot, identity-scoped confirmation.
        val manager = service.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val component = ComponentName(service, service.javaClass)
        return manager.getRunningServices(Int.MAX_VALUE)?.any {
            it.service == component && it.uid == Process.myUid() && it.pid == Process.myPid() &&
                it.foreground
        } == true
    }
}
