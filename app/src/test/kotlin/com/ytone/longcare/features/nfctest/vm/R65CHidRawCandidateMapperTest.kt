package com.ytone.longcare.features.nfctest.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R65CHidRawCandidateMapperTest {

    @Test
    fun `hex-like assembled chars produce raw and filtered candidates`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "04:26:FA:FA:05:1F:91",
            assembledChars = "0426FAFA051F91",
        )

        assertEquals(R65CHidCandidateKind.RawAssembled, candidates[0].kind)
        assertEquals("0426FAFA051F91", candidates[0].value)
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.HexFiltered && it.value == "0426FAFA051F91" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "looks like 14 hex" })
    }

    @Test
    fun `numeric assembled chars produce decimal and reversed four byte candidates`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "4210697732",
            assembledChars = "4210697732",
        )

        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.DecimalToHex && it.value == "FAFA2604" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.ReversedFourByteHex && it.value == "0426FAFA" })
        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "numeric only" })
    }

    @Test
    fun `non ascii input is classified as polluted`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "901948\u4E0DEA8\u60F30\u60F3",
            assembledChars = "901948\u4E0DEA8\u60F30\u60F3",
        )

        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "contains non-ASCII" })
    }
}
