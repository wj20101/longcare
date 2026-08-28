package com.ytone.longcare.platform.sales

import com.ytone.longcare.integration.qlz.QlzSdkClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesEvaluationDeviceGatewayTest {
    @Test
    fun `gateway delegates device identity and observable connection name unchanged`() {
        val client =
            mockk<QlzSdkClient> {
                every { getDeviceId() } returns Result.success("device-exact")
                every { getConnectedDeviceName() } returns "vendor-device-name"
            }
        val gateway = SalesEvaluationDeviceGateway(client)

        assertEquals("device-exact", gateway.getDeviceId().getOrThrow())
        assertEquals("vendor-device-name", gateway.getConnectedDeviceName())
        verify(exactly = 1) { client.getDeviceId() }
        verify(exactly = 1) { client.getConnectedDeviceName() }
    }
}
