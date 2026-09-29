package com.ytone.longcare.platform.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 真实 Compose/ActivityResult 渲染，使用本地权限结果替身，不修改设备权限或业务数据。 */
class LocationPermissionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun approximate_only_opens_settings_and_return_rechecks_precise_permission() {
        verifySettingsReturn(grantOnReturn = true)
    }

    @Test fun settings_return_without_permission_remains_denied() {
        verifySettingsReturn(grantOnReturn = false)
    }

    private fun verifySettingsReturn(grantOnReturn: Boolean) {
        var granted = 0
        var denied = 0
        var finePermission = false
        var settingsRequestCode = -1
        var settingsIntent: Intent? = null
        lateinit var appContext: Context
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) {
                when (contract) {
                    is ActivityResultContracts.RequestMultiplePermissions -> dispatchResult(requestCode,
                        mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false,
                            Manifest.permission.ACCESS_COARSE_LOCATION to true))
                    is ActivityResultContracts.StartActivityForResult -> {
                        settingsRequestCode = requestCode
                        settingsIntent = input as Intent
                    }
                    else -> error("Unexpected activity result contract")
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        compose.setContent {
            appContext = LocalContext.current
            val permissionContext = object : ContextWrapper(appContext) {
                override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                    if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
                        if (finePermission) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    } else super.checkPermission(permission, pid, uid)
            }
            CompositionLocalProvider(LocalContext provides permissionContext,
                LocalActivityResultRegistryOwner provides owner) {
                LongCareTheme {
                    val launcher = rememberLocationPermissionLauncher({ granted++ }, { denied++ })
                    TextButton(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("定位测试") }
                }
            }
        }
        compose.onNodeWithText("定位测试").performClick()
        compose.onNodeWithText(appContext.getString(R.string.location_permission_settings_title)).assertExists()
        compose.runOnIdle { assertEquals(0, granted); assertEquals(1, denied) }
        compose.onNodeWithText(appContext.getString(R.string.location_permission_settings_open)).performClick()
        compose.runOnIdle {
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsIntent?.action)
            assertEquals("package:${appContext.packageName}", settingsIntent?.dataString)
            finePermission = grantOnReturn
            registry.dispatchResult(settingsRequestCode, ActivityResult(Activity.RESULT_OK, null))
        }
        compose.runOnIdle {
            assertEquals(if (grantOnReturn) 1 else 0, granted)
            assertEquals(if (grantOnReturn) 1 else 2, denied)
        }
        compose.onNodeWithText(appContext.getString(R.string.location_permission_settings_title)).assertDoesNotExist()
    }
}
