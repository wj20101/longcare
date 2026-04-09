package com.ytone.longcare.features.nfctest.vm

import java.math.BigInteger

fun buildR65CHidCandidateValues(
    textFieldValue: String,
    assembledChars: String,
): List<R65CHidCandidateValue> {
    val candidates = mutableListOf<R65CHidCandidateValue>()

    if (assembledChars.isNotEmpty()) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.RawAssembled,
            value = assembledChars,
            note = "raw assembled chars",
        )
    }

    if (textFieldValue.isNotEmpty()) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.RawText,
            value = textFieldValue,
            note = "raw text field",
        )
    }

    val hexFiltered = assembledChars.filter { it.isHexLikeChar() }
    if (hexFiltered.isNotEmpty()) {
        val hexNote = when (hexFiltered.length) {
            8 -> "looks like 8 hex"
            14 -> "looks like 14 hex"
            else -> "hex-only filtered"
        }

        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.HexFiltered,
            value = hexFiltered,
            note = hexNote,
        )
    }

    val isNumericOnly = assembledChars.isNotEmpty() && assembledChars.all { it.isDigit() }
    val containsNonAscii = assembledChars.any { it.code > 0x7F }

    if (isNumericOnly) {
        val decimalHex = BigInteger(assembledChars).toString(16).uppercase()
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.DecimalToHex,
            value = decimalHex,
            note = "decimal interpreted as hex",
        )

        if (decimalHex.length <= 8) {
            val padded = decimalHex.padStart(8, '0')
            val reversed = padded.chunked(2).asReversed().joinToString(separator = "")
            candidates += R65CHidCandidateValue(
                kind = R65CHidCandidateKind.ReversedFourByteHex,
                value = reversed,
                note = "byte-reversed padded 4-byte hex",
            )
        }

        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = assembledChars,
            note = "numeric only",
        )
    } else if (containsNonAscii) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = assembledChars,
            note = "contains non-ASCII",
        )
    } else if (hexFiltered.length == 8) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = assembledChars,
            note = "looks like 8 hex",
        )
    } else if (hexFiltered.length == 14) {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = assembledChars,
            note = "looks like 14 hex",
        )
    } else {
        candidates += R65CHidCandidateValue(
            kind = R65CHidCandidateKind.Classification,
            value = assembledChars,
            note = "invalid for business UID",
        )
    }

    return candidates
}

private fun Char.isHexLikeChar(): Boolean {
    return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
