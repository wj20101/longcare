package com.ytone.longcare.integration.qlz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QlzBluetoothEnvironmentTest {
    @Test
    fun `api 31 uses nearby device permissions without a location-service gate`() {
        assertEquals(
            QlzBluetoothPermissionProfile.NEARBY_DEVICES,
            qlzBluetoothPermissionProfile(31),
        )
        assertNull(
            evaluateQlzBluetoothEnvironment(
                readySnapshot(apiLevel = 31).copy(locationServiceEnabled = false)
            )
        )
    }

    @Test
    fun `api 30 requires fine location and enabled location services`() {
        assertEquals(
            QlzBluetoothPermissionProfile.FINE_LOCATION,
            qlzBluetoothPermissionProfile(30),
        )
        assertEquals(
            QlzEvaluationIssue.LOCATION_SERVICE_DISABLED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot(apiLevel = 30).copy(locationServiceEnabled = false)
            ),
        )
    }

    @Test
    fun `missing permission and unavailable BLE have deterministic blockers`() {
        assertEquals(
            QlzEvaluationIssue.PERMISSION_REQUIRED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot(apiLevel = 31).copy(permissionsGranted = false)
            ),
        )
        assertEquals(
            QlzEvaluationIssue.BLUETOOTH_UNSUPPORTED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot(apiLevel = 31).copy(hasBleFeature = false)
            ),
        )
        assertEquals(
            QlzEvaluationIssue.BLUETOOTH_DISABLED,
            evaluateQlzBluetoothEnvironment(
                readySnapshot(apiLevel = 31).copy(bluetoothEnabled = false)
            ),
        )
    }

    private fun readySnapshot(apiLevel: Int) =
        QlzBluetoothEnvironmentSnapshot(
            apiLevel = apiLevel,
            hasBleFeature = true,
            permissionsGranted = true,
            hasBluetoothAdapter = true,
            bluetoothEnabled = true,
            locationServiceEnabled = true,
        )
}
