package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.window.layout.WindowMetricsCalculator
import com.ytone.longcare.di.DeviceIdStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.pm.PackageInfoCompat

@Singleton
class DeviceUtils @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    @param:DeviceIdStorage private val deviceIdPrefs: SharedPreferences,
    private val privacyConsentManager: PrivacyConsentManager,
) {
    @Volatile
    private var cachedAppInstanceId: String? = null

    /**
     * Returns the private GUID for this installation. It is created only after privacy consent and
     * never derives from Android ID, another hardware identifier, or a legacy persisted value.
     */
    fun getAppInstanceId(): String {
        check(privacyConsentManager.isPrivacyConsented) {
            "App instance GUID is unavailable before privacy consent"
        }
        cachedAppInstanceId?.let { return it }
        synchronized(this) {
            check(privacyConsentManager.isPrivacyConsented) {
                "App instance GUID is unavailable before privacy consent"
            }
            cachedAppInstanceId?.let { return it }
            val storedId = deviceIdPrefs.getString(DeviceRuntimeState.APP_INSTANCE_GUID_KEY, null)
            if (!storedId.isNullOrBlank()) {
                cachedAppInstanceId = storedId
                return storedId
            }
            val newUuid = UUID.randomUUID().toString()
            val committed = deviceIdPrefs.edit()
                .putString(DeviceRuntimeState.APP_INSTANCE_GUID_KEY, newUuid)
                .commit()
            check(committed) { "Unable to persist app instance GUID" }
            cachedAppInstanceId = newUuid
            return newUuid
        }
    }


    /**
     * 获取设备型号。
     * 例如："Pixel 5", "SM-G998B"
     */
    fun getDeviceModel(): String = Build.MODEL

    /**
     * 获取设备制造商。
     * 例如："Google", "samsung"
     */
    fun getDeviceManufacturer(): String = Build.MANUFACTURER

    /**
     * 获取设备品牌。
     * 通常与制造商类似或更通用。例如："google", "samsung"
     */
    fun getDeviceBrand(): String = Build.BRAND

    /**
     * 获取 Android 系统版本号 (API Level)。
     * 例如：30 (Android 11), 33 (Android 13)
     */
    fun getAndroidApiLevel(): Int = Build.VERSION.SDK_INT

    /**
     * 获取 Android 系统版本名称。
     * 例如："11", "13"
     */
    fun getAndroidVersionName(): String = Build.VERSION.RELEASE

    /**
     * 获取设备当前默认语言代码。
     * 例如："en", "zh"
     */
    fun getDeviceLanguage(): String {
        return Locale.getDefault().language
    }

    /**
     * 获取设备当前默认国家代码。
     * 例如："US", "CN"
     * 优先尝试从 TelephonyManager 获取网络国家，失败则回退到 Locale。
     */
    fun getDeviceCountry(): String {
        try {
            val telephonyManager =
                ContextCompat.getSystemService(applicationContext,TelephonyManager::class.java)
            telephonyManager?.networkCountryIso?.let {
                if (it.isNotBlank()) return it.uppercase(Locale.ROOT) // 使用 Locale.ROOT 保证大小写转换的一致性
            }
        } catch (e: Exception) {
            logE("无法从 TelephonyManager 获取国家代码", throwable = e)
        }
        return Locale.getDefault().country.uppercase(Locale.ROOT)
    }

    /**
     * 获取当前窗口的宽度（像素）。
     * 使用 Jetpack WindowManager 库 (`androidx.window.layout.WindowMetricsCalculator`)。
     *
     * 注意：此方法使用 applicationContext，通常返回显示区域的尺寸。
     * 为获得特定 Activity 的精确窗口尺寸，应在该 Activity 内调用并传入其 Context。
     *
     * @return 当前窗口的宽度（像素）。
     */
    fun getWindowWidth(): Int {
        val windowMetrics =
            WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(applicationContext)
        return windowMetrics.bounds.width()
    }

    /**
     * 获取当前窗口的高度（像素）。
     * 使用 Jetpack WindowManager 库 (`androidx.window.layout.WindowMetricsCalculator`)。
     *
     * 注意：此方法使用 applicationContext，通常返回显示区域的尺寸。
     * 为获得特定 Activity 的精确窗口尺寸，应在该 Activity 内调用并传入其 Context。
     *
     * @return 当前窗口的高度（像素）。
     */
    fun getWindowHeight(): Int {
        val windowMetrics =
            WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(applicationContext)
        return windowMetrics.bounds.height()
    }

    /**
     * 获取屏幕密度 (dpi)。
     * (e.g., 160 (mdpi), 240 (hdpi), 320 (xhdpi))
     * 这是标准 API，没有特定的 Jetpack 兼容替代。
     *
     * @return 屏幕密度 (dots per inch)。
     */
    fun getScreenDensityDpi(): Int {
        return applicationContext.resources.displayMetrics.densityDpi
    }

    /**
     * 获取屏幕密度因子。
     * (例如：1.0f for mdpi, 1.5f for hdpi, 2.0f for xhdpi)
     * 这是标准 API，没有特定的 Jetpack 兼容替代。
     *
     * @return 屏幕密度因子。
     */
    fun getScreenDensityFactor(): Float {
        return applicationContext.resources.displayMetrics.density
    }

    private fun getPackageInfo(): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
                applicationContext.packageManager.getPackageInfo(
                    applicationContext.packageName, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                applicationContext.packageManager.getPackageInfo(
                    applicationContext.packageName,
                    0
                )
            }
        } catch (e: Exception) {
            // Log.e("DeviceUtils", "Failed to get PackageInfo", e)
            null
        }
    }

    /**
     * 获取应用版本名称。
     *
     * @return 应用版本名称，如果获取失败则为 "Unknown"。
     */
    fun getAppVersionName(): String {
        return getPackageInfo()?.versionName ?: "Unknown"
    }

    /**
     * 获取应用版本号 (longVersionCode)。
     *
     * @return 应用版本号，如果获取失败则为 -1L。
     */
    fun getAppVersionCode(): Long {
        val packageInfo = getPackageInfo()

        return packageInfo?.let {
            PackageInfoCompat.getLongVersionCode(it)
        } ?: -1L
    }
}
