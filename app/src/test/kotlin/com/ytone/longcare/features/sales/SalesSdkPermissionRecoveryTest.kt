package com.ytone.longcare.features.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesSdkPermissionRecoveryTest {
    @Test
    fun `denied BLE permissions can be granted and retried for the same flow`() {
        val permissions = arrayOf("bluetooth-scan", "bluetooth-connect")
        val granted = mutableSetOf<String>()

        assertEquals(
            permissions.toList(),
            missingSalesSdkPermissions(permissions, granted::contains),
        )

        granted += permissions

        assertTrue(
            missingSalesSdkPermissions(permissions, granted::contains).isEmpty()
        )
    }
}
