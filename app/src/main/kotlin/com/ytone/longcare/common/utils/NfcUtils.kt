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
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.R

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
     * @return 显示的 AlertDialog 对象，调用者可以管理其生命周期
     */
    fun showEnableNfcDialog(
        activity: Activity,
        title: String? = null,
        message: String? = null,
    ): AlertDialog {
        return AlertDialog.Builder(activity)
            .setTitle(title ?: activity.getString(R.string.nfc_disabled_title))
            .setMessage(message ?: activity.getString(R.string.nfc_disabled_message))
            .setPositiveButton(activity.getString(R.string.nfc_settings_action)) { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
            .setNegativeButton(activity.getString(R.string.nfc_settings_cancel), null)
            .show()
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
        val tagIntentFilter = NfcIntentActions.createLegacyTagDiscoveredFilter()
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
    fun getTagFromIntent(intent: Intent): Tag? = NfcIntentDataUtils.getTagFromIntent(intent)

    /**
     * 从 Intent 中提取 NDEF 消息。
     * @return Array of NdefMessage, or null if no NDEF messages are present.
     */
    fun getNdefMessagesFromIntent(intent: Intent): Array<NdefMessage>? =
        NfcIntentDataUtils.getNdefMessagesFromIntent(intent)

    /**
     * 解析 NDEF 消息中的第一个文本记录 (TNF_WELL_KNOWN, RTD_TEXT)。
     * @return The text content, or null if no text record is found.
     */
    fun parseTextFromNdefMessage(ndefMessage: NdefMessage): String? =
        NfcIntentDataUtils.parseTextFromNdefMessage(ndefMessage)

    /**
     * 解析 NDEF 消息中的第一个 URI 记录 (TNF_WELL_KNOWN, RTD_URI)。
     * @return The URI string, or null if no URI record is found.
     */
    fun parseUriFromNdefMessage(ndefMessage: NdefMessage): String? =
        NfcIntentDataUtils.parseUriFromNdefMessage(ndefMessage)

    /**
     * 获取 Tag 的技术列表。
     */
    fun getTagTechList(tag: Tag?): List<String> = NfcIntentDataUtils.getTagTechList(tag)

    /**
     * 将字节数组转换为十六进制字符串。
     */
    fun bytesToHexString(bytes: ByteArray?): String = NfcIntentDataUtils.bytesToHexString(bytes)

    /**
     * 将十六进制字符串转换为字节数组。
     */
    fun hexStringToBytes(hexString: String?): ByteArray? = NfcIntentDataUtils.hexStringToBytes(hexString)

    // --- 更多高级功能可以添加 ---
    // 例如：写入 NDEF 消息到标签，处理特定的 Tag 技术 (NfcA, IsoDep 等)

    fun readNdefMessageFromTag(tag: Tag): NdefMessage? =
        NfcNdefUtils.readNdefMessageFromTag(tag)

    fun writeNdefMessageToTag(tag: Tag, message: NdefMessage): Boolean =
        NfcNdefUtils.writeNdefMessageToTag(tag, message)

    fun createTextNdefRecord(
        text: String,
        languageCode: String = "en",
        encodeInUtf8: Boolean = true
    ): NdefRecord = NfcNdefUtils.createTextNdefRecord(text, languageCode, encodeInUtf8)

    fun createUriNdefRecord(uriString: String): NdefRecord? =
        NfcNdefUtils.createUriNdefRecord(uriString)
}
