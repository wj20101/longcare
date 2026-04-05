package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRfidTagParserTest {

    private val parser = ExternalRfidTagParser()

    @Test
    fun `normalize trims whitespace and uppercases tag ids`() {
        assertEquals("01AB9F", parser.normalize(" 01ab9f\r\n"))
    }

    @Test
<<<<<<< HEAD
    fun `normalize removes inner spaces`() {
        assertEquals("01AB", parser.normalize("  01 ab  "))
    }

    @Test
=======
>>>>>>> 1d86300 (feat: add NFC fallback scan contracts)
    fun `normalize rejects blank payloads and non alphanumeric values`() {
        assertNull(parser.normalize("   "))
        assertNull(parser.normalize("01-AB"))
    }
}
