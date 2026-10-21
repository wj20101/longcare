package com.ytone.longcare.features.location.core

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.location.LocationManagerCompat
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLocationRuntimeReadiness @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRuntimeReadiness {
    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    override fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /** A location foreground Service alone does not make the app UI visible. */
    fun isAppVisible(): Boolean? = read { isLocationProcessVisible() }

    /** Read only when reporting an episode; null means unavailable, never a guessed false. */
    fun diagnosticSnapshot(): Map<String, Any?> {
        val connectivity = read { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
        // Preserve the distinction between an unavailable getter and a confirmed absent network.
        val networkRead = connectivity?.let { runCatching { it.activeNetwork } }
        val networkAvailable = networkRead?.map { it != null }?.getOrNull()
        val capabilities = networkRead?.getOrNull()?.let { network ->
            read { connectivity?.getNetworkCapabilities(network) }
        }
        val power = read { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
        val activity = read { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        return mapOf(
            "androidApi" to Build.VERSION.SDK_INT,
            "appVersionCode" to read {
                val manager = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    manager.getPackageInfo(context.packageName, 0)
                }
                PackageInfoCompat.getLongVersionCode(info)
            },
            "networkAvailable" to networkAvailable,
            "networkValidated" to when (networkAvailable) {
                false -> false
                true -> capabilities?.let { read { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } }
                null -> null
            },
            "networkTransport" to when {
                networkAvailable == false -> "NONE"
                capabilities == null -> null
                else -> read {
                    when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                        else -> "OTHER"
                    }
                }
            },
            "deviceIdle" to read { power?.isDeviceIdleMode },
            "deviceLightIdle" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                read { power?.isDeviceLightIdleMode }
            } else null,
            "screenOn" to read { power?.isInteractive },
            "powerSave" to read { power?.isPowerSaveMode },
            "batteryExempt" to read { power?.isIgnoringBatteryOptimizations(context.packageName) },
            "backgroundRestricted" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                read { activity?.isBackgroundRestricted }
            } else null,
            // Data Saver describes background metered-network policy, not current UID reachability.
            "restrictBackgroundStatus" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                read { connectivity?.restrictBackgroundStatus }?.takeIf { status ->
                    status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED ||
                        status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED ||
                        status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                }
            } else null,
            // Enabled is configuration only; it does not establish that standby is currently active.
            "lowPowerStandbyEnabled" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                read { power?.isLowPowerStandbyEnabled }
            } else null,
        )
    }

    private inline fun <T> read(block: () -> T): T? = try { block() } catch (_: Exception) { null }
}

internal fun isLocationProcessVisible(): Boolean {
    val process = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(process)
    return process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
}
