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

        assertEquals(4, candidates.size)

        assertEquals(R65CHidCandidateKind.RawAssembled, candidates[0].kind)
        assertEquals("0426FAFA051F91", candidates[0].value)

        assertEquals(R65CHidCandidateKind.RawText, candidates[1].kind)
        assertEquals("04:26:FA:FA:05:1F:91", candidates[1].value)

        assertEquals(R65CHidCandidateKind.HexFiltered, candidates[2].kind)
        assertEquals("0426FAFA051F91", candidates[2].value)
        assertEquals("looks like 14 hex", candidates[2].note)

        assertEquals(R65CHidCandidateKind.Classification, candidates[3].kind)
        assertEquals("looks like 14 hex", candidates[3].note)
    }

    @Test
    fun `numeric assembled chars produce decimal and reversed four byte candidates`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "4210697732",
            assembledChars = "4210697732",
        )

        assertEquals(5, candidates.size)

        assertEquals(R65CHidCandidateKind.RawAssembled, candidates[0].kind)
        assertEquals("4210697732", candidates[0].value)

        assertEquals(R65CHidCandidateKind.RawText, candidates[1].kind)
        assertEquals("4210697732", candidates[1].value)

        assertEquals(R65CHidCandidateKind.DecimalToHex, candidates[2].kind)
        assertEquals("FAFA2604", candidates[2].value)

        assertEquals(R65CHidCandidateKind.ReversedFourByteHex, candidates[3].kind)
        assertEquals("0426FAFA", candidates[3].value)

        assertEquals(R65CHidCandidateKind.Classification, candidates[4].kind)
        assertEquals("numeric only", candidates[4].note)
    }

    @Test
    fun `non ascii input is classified as polluted`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "901948\u4E0DEA8\u60F30\u60F3",
            assembledChars = "901948\u4E0DEA8\u60F30\u60F3",
        )

        assertTrue(candidates.any { it.kind == R65CHidCandidateKind.Classification && it.note == "contains non-ASCII" })
    }

    @Test
    fun `hex filtered candidate is normalized to uppercase`() {
        val candidates = buildR65CHidCandidateValues(
            textFieldValue = "04:26:fa:fa:05:1f:91",
            assembledChars = "0426fafa051f91",
        )

        assertEquals(R65CHidCandidateKind.HexFiltered, candidates[2].kind)
        assertEquals("0426FAFA051F91", candidates[2].value)
    }
}
