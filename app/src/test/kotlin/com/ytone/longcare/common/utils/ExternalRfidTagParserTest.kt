package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRfidTagParserTest {

    private val parser = ExternalRfidTagParser()

    @Test
    fun `normalize trims separators and uppercases NFC style hex ids`() {
        assertEquals("0426FAFA051F91", parser.normalize(" 0426-fa fa_051f91 "))
    }

    @Test
    fun `normalize rejects blank non hex and invalid length payloads`() {
        assertNull(parser.normalize("   "))
        assertNull(parser.normalize("01-AB-Z9"))
        assertNull(parser.normalize("123456789"))
        assertNull(parser.normalize("中文"))
    }
}
