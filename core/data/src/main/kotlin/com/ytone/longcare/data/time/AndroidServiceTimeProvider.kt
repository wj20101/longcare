package com.ytone.longcare.data.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.ytone.longcare.domain.time.ServiceTimeProvider
import com.ytone.longcare.model.ServiceTimeSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidServiceTimeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServiceTimeProvider {
    // A process cannot survive a reboot. Read this stable identity once, outside the timer hot path.
    private val bootCount: Int? by lazy {
        // Unknown boot identity must remain unknown; elapsed time cannot then be restored safely.
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                .takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    override fun snapshot(): ServiceTimeSnapshot {
        val currentBootCount = bootCount
        return ServiceTimeSnapshot(
            wallTimeMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            bootCount = currentBootCount,
        )
    }
}
