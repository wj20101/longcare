package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.ytone.longcare.model.LoginLogParamModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHomeLoginLogInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HomeLoginLogInfoProvider {

    override fun build(): LoginLogParamModel = LoginLogParamModel(
        phoneSystem = "Android",
        phoneVersion = Build.VERSION.RELEASE.orEmpty(),
        networkType = resolveNetworkType(),
        networkOperator = resolveNetworkOperator(),
    )

    private fun resolveNetworkType(): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "NONE"
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    private fun resolveNetworkOperator(): String {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return ""
        return telephonyManager.networkOperatorName.orEmpty()
    }
}
