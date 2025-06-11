package com.ytone.longcare.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.window.layout.WindowMetricsCalculator
import com.ytone.longcare.di.DeviceIdStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat

@Singleton
class DeviceUtils @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    @DeviceIdStorage private val deviceIdPrefs: SharedPreferences
) {

    companion object {
        private const val PREFS_GENERATED_ID_KEY = "generated_app_instance_id" // 存储我们生成的UUID
        private const val INVALID_ANDROID_ID_VALUE = "9774d56d682e549c" // 常见的不可靠ANDROID_ID
    }

    /**
     * 获取 ANDROID_ID。
     *
     * @return ANDROID_ID 字符串，如果获取失败或无效则为 null。
     */
    @SuppressLint("HardwareIds")
    private fun fetchAndroidIdInternal(): String? { // 改为 private 供内部使用
        return try {
            val androidId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            // Log.d("DeviceUtils", "Fetched ANDROID_ID: $androidId")
            if (androidId.isNullOrBlank() || androidId == INVALID_ANDROID_ID_VALUE) {
                null // 如果为空、空白或特定无效值，则视为无效
            } else {
                androidId
            }
        } catch (e: Exception) {
            // Log.e("DeviceUtils", "Failed to get ANDROID_ID", e)
            null
        }
    }

    /**
     * 公开获取 ANDROID_ID 的方法，如果需要直接访问它（但通常不推荐作为唯一设备标识符）。
     * 返回原始获取到的 ANDROID_ID，可能为 null 或特定无效值。
     */
    @SuppressLint("HardwareIds")
    fun getRawAndroidId(): String? {
        return try {
            Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }


    /**
     * 获取一个稳定的、针对应用安装实例的唯一 ID。
     *
     * 优先顺序：
     * 1. 如果 SharedPreferences 中已存储一个生成的 ID，则使用它。
     * 2. 尝试获取 ANDROID_ID：
     *    a. 如果 ANDROID_ID 有效且不是特殊值，则使用 ANDROID_ID 作为此应用实例的 ID，并将其存储。
     *       (注意：如果应用卸载重装，ANDROID_ID 在 Android 8+ 会对新应用不同)
     * 3. 如果 ANDROID_ID 无效或获取失败，则生成一个新的 UUID，存储并使用它。
     *
     * 这种策略试图在 ANDROID_ID 可用时利用它，同时提供 UUID 作为后备，
     * 但主要稳定性依赖于 SharedPreferences 中的值。
     * 如果应用被卸载（且数据未备份）或用户清除应用数据，此 ID 将会重新生成。
     *
     * @return 应用安装实例的唯一 ID。
     */
    @Synchronized
    fun getAppInstanceId(): String {
        // 1. 检查是否已存在生成的ID (通常是UUID)
        val storedId = deviceIdPrefs.getString(PREFS_GENERATED_ID_KEY, null)
        if (!storedId.isNullOrEmpty()) {
            // Log.d("DeviceUtils", "Returning stored generated ID: $storedId")
            return storedId
        }

        // 2. 如果没有存储的ID，尝试使用 ANDROID_ID (如果它“好”)
        val androidId = fetchAndroidIdInternal() // 获取经过处理的 ANDROID_ID
        if (androidId != null) {
            // Log.d("DeviceUtils", "Using valid ANDROID_ID as app instance ID: $androidId")
            // 将有效的 ANDROID_ID 存储起来，作为此“安装实例”的 ID
            // 后续调用将直接返回这个存储的 ID，即使 ANDROID_ID 因某种原因（非卸载）变化
            // 但如果应用数据被清除，这里会重新执行，可能会获取到新的 ANDROID_ID (如果系统分配了新的)
            deviceIdPrefs.edit { putString(PREFS_GENERATED_ID_KEY, androidId) }
            return androidId
        }

        // 3. 如果 ANDROID_ID 无效或不可用，生成新的 UUID
        // Log.d("DeviceUtils", "ANDROID_ID is invalid or null, generating new UUID.")
        val newUuid = UUID.randomUUID().toString()
        deviceIdPrefs.edit { putString(PREFS_GENERATED_ID_KEY, newUuid) }
        return newUuid
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
            // Log.w("DeviceUtils", "Cannot get country from TelephonyManager", e)
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