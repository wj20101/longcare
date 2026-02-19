package com.ytone.longcare.common.utils

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.core.content.IntentCompat

object NfcIntentDataUtils {

    fun getTagFromIntent(intent: Intent): Tag? {
        val action = intent.action
        if (
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        }
        return null
    }

    fun getNdefMessagesFromIntent(intent: Intent): Array<NdefMessage>? {
        val action = intent.action
        if (
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            val rawMessages = IntentCompat.getParcelableArrayExtra(
                intent,
                NfcAdapter.EXTRA_NDEF_MESSAGES,
                NdefMessage::class.java
            )
            return rawMessages?.filterIsInstance<NdefMessage>()?.toTypedArray()
        }
        return null
    }

    fun parseTextFromNdefMessage(ndefMessage: NdefMessage): String? {
        ndefMessage.records.forEach { record ->
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                try {
                    val payload = record.payload
                    val status = payload[0].toInt()
                    val textEncoding = if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
                    val languageCodeLength = status and 0x3F
                    return String(
                        payload,
                        languageCodeLength + 1,
                        payload.size - languageCodeLength - 1,
                        textEncoding
                    )
                } catch (e: Exception) {
                    logE(
                        message = "Error parsing NDEF text record",
                        tag = "NfcIntentDataUtils",
                        throwable = e
                    )
                    return null
                }
            }
        }
        return null
    }

    fun parseUriFromNdefMessage(ndefMessage: NdefMessage): String? {
        ndefMessage.records.forEach { record ->
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
                return try {
                    record.toUri().toString()
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    fun getTagTechList(tag: Tag?): List<String> = tag?.techList?.toList() ?: emptyList()

    fun bytesToHexString(bytes: ByteArray?): String =
        bytes?.joinToString("") { String.format("%02X", it) } ?: ""

    fun hexStringToBytes(hexString: String?): ByteArray? {
        if (hexString == null || hexString.length % 2 != 0) return null

        val data = ByteArray(hexString.length / 2)
        var i = 0
        while (i < hexString.length) {
            data[i / 2] = (
                (Character.digit(hexString[i], 16) shl 4) +
                    Character.digit(hexString[i + 1], 16)
                ).toByte()
            i += 2
        }
        return data
    }
}
