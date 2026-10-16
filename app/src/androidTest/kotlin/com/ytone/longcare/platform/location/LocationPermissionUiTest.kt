package com.ytone.longcare.platform.location

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
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
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.PermissionPurposeNotice
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Local permission results only: no device permission or care order is changed. */
class LocationPermissionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun approximate_only_respects_denial_without_a_settings_dialog() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("获取当前位置").performClick()
        compose.runOnIdle {
            assertEquals(0, fixture.granted)
            assertEquals(1, fixture.denied)
            assertEquals(0, fixture.settingsRequests)
        }
        compose.onNodeWithText("获取当前位置").assertExists()
        compose.onNodeWithText("前往设置").assertDoesNotExist()
    }

    @Test fun location_request_does_not_bundle_notification_permission() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("获取当前位置").performClick()
        compose.runOnIdle {
            assertEquals(setOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), fixture.requested.toSet())
        }
    }

    @Test fun callback_payload_does_not_override_actual_denied_permission() {
        val fixture = Fixture().apply {
            payload = mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true)
        }
        render(fixture)
        compose.onNodeWithText("获取当前位置").performClick()
        compose.runOnIdle {
            assertEquals(0, fixture.granted)
            assertEquals(1, fixture.denied)
        }
    }

    @Test fun explicit_retry_reads_actual_permission_after_user_changes_system_settings() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("获取当前位置").performClick()
        compose.runOnIdle { fixture.finePermission = true }
        compose.onNodeWithText("获取当前位置").performClick()
        compose.runOnIdle {
            assertEquals(1, fixture.granted)
            assertEquals(1, fixture.denied)
            assertEquals(0, fixture.settingsRequests)
        }
    }

    @Test fun permission_explanation_offers_settings_only_as_an_explicit_user_action() {
        val actions = mutableListOf<String>()
        compose.setContent {
            LongCareTheme {
                PermissionPurposeDialog(
                    notice = PermissionPurposeNotice("定位权限", "用于本次位置获取", "允许定位", "取消"),
                    onConfirm = { actions += "request" },
                    onDismiss = { actions += "dismiss" },
                    onOpenSettings = { actions += "settings" },
                )
            }
        }
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithText("前往应用设置").performClick()
        compose.runOnIdle { assertEquals(listOf("settings"), actions) }
    }

    private fun render(fixture: Fixture) {
        compose.setContent {
            val base = LocalContext.current
            val context = object : ContextWrapper(base) {
                override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                    if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
                        if (fixture.finePermission) PackageManager.PERMISSION_GRANTED
                        else PackageManager.PERMISSION_DENIED
                    } else super.checkPermission(permission, pid, uid)
            }
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = fixture.registry
            }
            CompositionLocalProvider(LocalContext provides context,
                LocalActivityResultRegistryOwner provides owner) {
                LongCareTheme {
                    val launcher = rememberLocationPermissionLauncher({ fixture.granted++ }, { fixture.denied++ })
                    TextButton(onClick = { launcher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions()) }) {
                        Text("获取当前位置")
                    }
                }
            }
        }
    }

    private class Fixture {
        var finePermission = false
        var granted = 0
        var denied = 0
        var settingsRequests = 0
        var requested = emptyList<String>()
        var payload = mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false,
            Manifest.permission.ACCESS_COARSE_LOCATION to true)
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) {
                when (contract) {
                    is ActivityResultContracts.RequestMultiplePermissions -> {
                        @Suppress("UNCHECKED_CAST")
                        requested = (input as Array<String>).toList()
                        dispatchResult(requestCode, payload)
                    }
                    is ActivityResultContracts.StartActivityForResult -> settingsRequests++
                    else -> error("Unexpected activity result contract")
                }
            }
        }
    }
}
