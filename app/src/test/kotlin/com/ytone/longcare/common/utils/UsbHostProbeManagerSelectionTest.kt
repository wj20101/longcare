package com.ytone.longcare.common.utils

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class UsbHostProbeManagerSelectionTest {

    @Test
    fun `findReadableTarget prefers bulk in endpoint across interfaces`() {
        val interruptIn = mockEndpoint(
            direction = UsbConstants.USB_DIR_IN,
            type = UsbConstants.USB_ENDPOINT_XFER_INT,
        )
        val bulkIn = mockEndpoint(
            direction = UsbConstants.USB_DIR_IN,
            type = UsbConstants.USB_ENDPOINT_XFER_BULK,
        )
        val firstInterface = mockInterface(interruptIn)
        val secondInterface = mockInterface(bulkIn)
        val device = mockDevice(firstInterface, secondInterface)

        val target = device.findReadableTarget()

        assertSame(secondInterface, target?.usbInterface)
        assertSame(bulkIn, target?.endpoint)
    }

    @Test
    fun `findReadableTarget falls back to interrupt in endpoint when bulk is unavailable`() {
        val interruptIn = mockEndpoint(
            direction = UsbConstants.USB_DIR_IN,
            type = UsbConstants.USB_ENDPOINT_XFER_INT,
        )
        val device = mockDevice(mockInterface(interruptIn))

        val target = device.findReadableTarget()

        assertSame(interruptIn, target?.endpoint)
    }

    @Test
    fun `findReadableTarget ignores unsupported and output endpoints`() {
        val bulkOut = mockEndpoint(
            direction = UsbConstants.USB_DIR_OUT,
            type = UsbConstants.USB_ENDPOINT_XFER_BULK,
        )
        val controlIn = mockEndpoint(
            direction = UsbConstants.USB_DIR_IN,
            type = UsbConstants.USB_ENDPOINT_XFER_CONTROL,
        )
        val device = mockDevice(mockInterface(bulkOut, controlIn))

        assertNull(device.findReadableTarget())
    }

    private fun mockDevice(vararg interfaces: UsbInterface): UsbDevice = mockk {
        every { interfaceCount } returns interfaces.size
        interfaces.forEachIndexed { index, usbInterface ->
            every { getInterface(index) } returns usbInterface
        }
    }

    private fun mockInterface(vararg endpoints: UsbEndpoint): UsbInterface = mockk {
        every { endpointCount } returns endpoints.size
        endpoints.forEachIndexed { index, endpoint ->
            every { getEndpoint(index) } returns endpoint
        }
    }

    private fun mockEndpoint(direction: Int, type: Int): UsbEndpoint = mockk {
        every { this@mockk.direction } returns direction
        every { this@mockk.type } returns type
    }
}
