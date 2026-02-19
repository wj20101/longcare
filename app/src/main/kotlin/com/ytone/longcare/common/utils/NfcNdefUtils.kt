package com.ytone.longcare.common.utils

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef

object NfcNdefUtils {

    /**
     * 尝试连接到标签并读取 NDEF 消息（如果标签支持 NDEF 技术）
     * 注意：这是一个阻塞操作，应该在后台线程执行。
     */
    fun readNdefMessageFromTag(tag: Tag): NdefMessage? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            ndef.ndefMessage
        } catch (e: Exception) {
            null
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 尝试连接到标签并写入 NDEF 消息（如果标签支持 NDEF 技术且可写）
     * 注意：这是一个阻塞操作，应该在后台线程执行。
     */
    fun writeNdefMessageToTag(tag: Tag, message: NdefMessage): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                return false
            }
            if (ndef.maxSize < message.toByteArray().size) {
                return false
            }
            ndef.writeNdefMessage(message)
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 创建一个简单的文本 NDEF 记录。
     */
    fun createTextNdefRecord(
        text: String,
        languageCode: String = "en",
        encodeInUtf8: Boolean = true
    ): NdefRecord {
        val langBytes = languageCode.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(if (encodeInUtf8) Charsets.UTF_8 else Charsets.UTF_16)

        val headerLength = 1 + langBytes.size
        val payload = ByteArray(headerLength + textBytes.size)

        payload[0] = (if (encodeInUtf8) 0x00 else 0x80).toByte()
        payload[0] = (payload[0].toInt() or langBytes.size).toByte()

        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, headerLength, textBytes.size)

        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }

    /**
     * 创建一个简单的 URI NDEF 记录。
     */
    fun createUriNdefRecord(uriString: String): NdefRecord? = NdefRecord.createUri(uriString)
}
