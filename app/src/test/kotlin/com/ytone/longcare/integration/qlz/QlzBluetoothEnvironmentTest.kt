package com.ytone.longcare.integration.qlz

import android.Manifest
import android.app.Application
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class QlzBluetoothEnvironmentTest {
    @Test
    @Config(sdk = [31, 33])
    fun `modern Android requests nearby devices and paired foreground location permissions`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            qlzRequiredRuntimePermissions(),
        )
    }

    @Test
    @Config(sdk = [24, 30])
    fun `legacy Android only requests fine location`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            qlzRequiredRuntimePermissions(),
        )
    }

    @Test
    fun `all versions block when location service is off and recover when enabled`() {
        assertEquals(
            QlzEvaluationIssue.LOCATION_SERVICE_DISABLED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(locationServiceEnabled = false)
            ),
        )
        assertNull(evaluateQlzBluetoothEnvironment(readySnapshot()))
    }

    @Test
    fun `nearby devices and approximate location alone cannot start scanning`() {
        val required = qlzRequiredRuntimePermissions()
        val granted = required.toSet() - Manifest.permission.ACCESS_FINE_LOCATION
        assertEquals(
            QlzEvaluationIssue.PERMISSION_REQUIRED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(permissionsGranted = required.all(granted::contains))
            ),
        )
        assertNull(
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(
                    permissionsGranted = required.all(
                        (granted + Manifest.permission.ACCESS_FINE_LOCATION)::contains
                    )
                )
            )
        )
    }

    @Test
    fun `permission retry retains the full fine and coarse pair`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/sales/SalesExperienceScreen.kt"
        ).readText().substringAfter("fun openEvaluationWithPermission()")
            .substringBefore("fun requestLocationPermission()")
        assertTrue(source.contains("sdkPermissionLauncher.launch(sdkPermissions)"))
        assertFalse(source.contains("launch(missing.toTypedArray())"))
    }

    @Test
    fun `missing permission and unavailable BLE have deterministic blockers`() {
        assertEquals(
            QlzEvaluationIssue.PERMISSION_REQUIRED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(permissionsGranted = false)
            ),
        )
        assertEquals(
            QlzEvaluationIssue.BLUETOOTH_UNSUPPORTED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(hasBleFeature = false)
            ),
        )
        assertEquals(
            QlzEvaluationIssue.BLUETOOTH_DISABLED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot().copy(bluetoothEnabled = false)
            ),
        )
    }

    private fun readySnapshot() =
        QlzBluetoothEnvironmentSnapshot(
            hasBleFeature = true,
            permissionsGranted = true,
            hasBluetoothAdapter = true,
            bluetoothEnabled = true,
            locationServiceEnabled = true,
        )
}
