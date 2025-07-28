package com.ytone.longcare.common.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.PendingIntentCompat
import androidx.core.content.IntentCompat

object NfcUtils {

    /**
     * 检查设备是否支持 NFC
     */
    fun isNfcSupported(context: Context): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null
    }

    /**
     * 检查 NFC 功能是否已启用
     */
    fun isNfcEnabled(context: Context): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter?.isEnabled == true
    }

    /**
     * 提示用户去设置中开启 NFC
     */
    fun showEnableNfcDialog(
        activity: Activity,
        title: String = "NFC Disabled",
        message: String = "Please enable NFC in settings to use this feature."
    ) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setPositiveButton("Settings") { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }.setNegativeButton("Cancel", null).show()
    }

    /**
     * 为 Activity 启用前台调度系统。
     * 当此 Activity 在前台时，它将优先处理 NFC intent。
     * 通常在 Activity 的 onResume() 中调用。
     *
     * @param activity The Activity to enable foreground dispatch for.
     * @param techLists Array of tech lists to filter for. Null for all.
     *                  Example: arrayOf(arrayOf(NfcA::class.java.name), arrayOf(Ndef::class.java.name))
     */
    fun enableForegroundDispatch(activity: Activity, techLists: Array<Array<String>>? = null) {
        if (!isNfcSupported(activity) || !isNfcEnabled(activity)) return

        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return

        // 创建一个 PendingIntent，当发现 NFC 标签时，系统会用它来启动我们的 Activity
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntentCompat.getActivity(
            activity,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            true // mutable = true，允许系统修改PendingIntent
        )

        // 定义 IntentFilter，用于声明我们感兴趣的 NFC 事件
        // NDEF_DISCOVERED 是最常用的，用于处理已格式化并包含 NDEF 消息的标签
        val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*") // 接收所有类型的 NDEF 数据
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Failed to add MIME type.", e)
            }
        }

        // 你也可以添加对其他 action 的支持，如 TAG_DISCOVERED 或 TECH_DISCOVERED
        val tagIntentFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val techIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)


        val intentFiltersArray = if (techLists.isNullOrEmpty()) {
            // 如果未指定 techLists，则监听 NDEF, TAG, 和 TECH discovered actions
            arrayOf(ndefIntentFilter, tagIntentFilter, techIntentFilter)
        } else {
            // 如果指定了 techLists，则主要关注 TECH_DISCOVERED action
            // NDEF_DISCOVERED 也可以保留，以防标签同时满足 NDEF 和特定技术
            arrayOf(ndefIntentFilter, techIntentFilter) // 或者仅 arrayOf(techIntentFilter) 如果只想严格匹配tech
        }

        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, intentFiltersArray, techLists)
    }

    /**
     * 为 Activity 禁用前台调度系统。
     * 通常在 Activity 的 onPause() 中调用。
     */
    fun disableForegroundDispatch(activity: Activity) {
        NfcAdapter.getDefaultAdapter(activity)?.disableForegroundDispatch(activity)
    }

    /**
     * 从 Intent 中提取 NFC Tag 对象。
     */
    fun getTagFromIntent(intent: Intent): Tag? {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            return IntentCompat.getParcelableExtra(intent,NfcAdapter.EXTRA_TAG,Tag::class.java)
        }
        return null
    }

    /**
     * 从 Intent 中提取 NDEF 消息。
     * @return Array of NdefMessage, or null if no NDEF messages are present.
     */
    fun getNdefMessagesFromIntent(intent: Intent): Array<NdefMessage>? {
        val action = intent.action
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action || NfcAdapter.ACTION_TAG_DISCOVERED == action || // TAG_DISCOVERED 也可以包含 NDEF
            NfcAdapter.ACTION_TECH_DISCOVERED == action   // TECH_DISCOVERED 也可以包含 NDEF
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

    /**
     * 解析 NDEF 消息中的第一个文本记录 (TNF_WELL_KNOWN, RTD_TEXT)。
     * @return The text content, or null if no text record is found.
     */
    fun parseTextFromNdefMessage(ndefMessage: NdefMessage): String? {
        ndefMessage.records.forEach { record ->
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                try {
                    val payload = record.payload
                    // 获取状态字节 (第一个字节)，它包含文本编码和语言代码长度
                    val status = payload[0].toInt()
                    // UTF-8 (bit 7 is 0) or UTF-16 (bit 7 is 1)
                    val textEncoding =
                        if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
                    // 语言代码长度 (bits 5 to 0)
                    val languageCodeLength = status and 0x3F
                    // 实际文本从语言代码之后开始
                    return String(
                        payload,
                        languageCodeLength + 1,
                        payload.size - languageCodeLength - 1,
                        textEncoding
                    )
                } catch (e: Exception) {
                    logE(
                        message = "Error parsing NDEF text record",
                        tag = "NfcUtils",
                        throwable = e
                    )
                    return null
                }
            }
        }
        return null
    }

    /**
     * 解析 NDEF 消息中的第一个 URI 记录 (TNF_WELL_KNOWN, RTD_URI)。
     * @return The URI string, or null if no URI record is found.
     */
    fun parseUriFromNdefMessage(ndefMessage: NdefMessage): String? {
        ndefMessage.records.forEach { record ->
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
                return try {
                    record.toUri().toString() // NdefRecord.toUri() (API 16+)
                } catch (e: Exception) {
                    // Log.e("NfcUtils", "Error parsing NDEF URI record", e) // Consider uncommenting or using a proper logger
                    null
                }
            }
        }
        return null
    }

    /**
     * 获取 Tag 的技术列表。
     */
    fun getTagTechList(tag: Tag?): List<String> {
        return tag?.techList?.toList() ?: emptyList()
    }

    /**
     * 将字节数组转换为十六进制字符串。
     */
    fun bytesToHexString(bytes: ByteArray?): String {
        return bytes?.joinToString("") { String.format("%02X", it) } ?: ""
    }

    /**
     * 将十六进制字符串转换为字节数组。
     */
    fun hexStringToBytes(hexString: String?): ByteArray? {
        if (hexString == null || hexString.length % 2 != 0) return null
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(
                hexString[i + 1],
                16
            )).toByte()
            i += 2
        }
        return data
    }

    // --- 更多高级功能可以添加 ---
    // 例如：写入 NDEF 消息到标签，处理特定的 Tag 技术 (NfcA, IsoDep 等)

    /**
     * 尝试连接到标签并读取 NDEF 消息（如果标签支持 NDEF 技术）
     * 注意：这是一个阻塞操作，应该在后台线程执行。
     */
    fun readNdefMessageFromTag(tag: Tag): NdefMessage? {
        val ndef = Ndef.get(tag) ?: return null // 标签不支持 NDEF
        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage // 或者 ndef.cachedNdefMessage 如果你只想读缓存
            ndefMessage
        } catch (e: Exception) {
            // Log.e("NfcUtils", "Error reading NDEF message from tag", e) // Consider uncommenting or using a proper logger
            null
        } finally {
            try {
                ndef.close()
            } catch (ex: Exception) {
                // Log.w("NfcUtils", "Error closing Ndef connection", ex) // Consider uncommenting
            }
        }
    }

    /**
     * 尝试连接到标签并写入 NDEF 消息（如果标签支持 NDEF 技术且可写）
     * 注意：这是一个阻塞操作，应该在后台线程执行。
     * @return true if write was successful, false otherwise.
     */
    fun writeNdefMessageToTag(tag: Tag, message: NdefMessage): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                // Log.w("NfcUtils", "Tag is not writable.")
                return false
            }
            if (ndef.maxSize < message.toByteArray().size) {
                // Log.w("NfcUtils", "Message is too large for this tag.")
                return false
            }
            ndef.writeNdefMessage(message)
            true
        } catch (e: Exception) {
            // Log.e("NfcUtils", "Error writing NDEF message to tag", e) // Consider uncommenting or using a proper logger
            false
        } finally {
            try {
                ndef.close()
            } catch (ex: Exception) {
                // Log.w("NfcUtils", "Error closing Ndef connection", ex) // Consider uncommenting
            }
        }
    }

    /**
     * 创建一个简单的文本 NDEF 记录。
     */
    fun createTextNdefRecord(
        text: String, languageCode: String = "en", encodeInUtf8: Boolean = true
    ): NdefRecord {
        val langBytes = languageCode.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(if (encodeInUtf8) Charsets.UTF_8 else Charsets.UTF_16)

        val headerLength = 1 + langBytes.size
        val payload = ByteArray(headerLength + textBytes.size)

        payload[0] = (if (encodeInUtf8) 0x00 else 0x80).toByte() // UTF-8/16 flag
        payload[0] = (payload[0].toInt() or langBytes.size).toByte() // Language code length

        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, headerLength, textBytes.size)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    /**
     * 创建一个简单的 URI NDEF 记录。
     */
    fun createUriNdefRecord(uriString: String): NdefRecord? {
        return NdefRecord.createUri(uriString) // API 14+
    }
}