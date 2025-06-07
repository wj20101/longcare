package com.ytone.longcare.common.utils

import com.tencent.mmkv.MMKV

object MMKVUtils {

    private var mmkv: MMKV? = MMKV.defaultMMKV()

    fun put(key: String, value: Any?) {
        when (value) {
            is String -> mmkv?.encode(key, value)
            is Int -> mmkv?.encode(key, value)
            is Boolean -> mmkv?.encode(key, value)
            is Float -> mmkv?.encode(key, value)
            is Long -> mmkv?.encode(key, value)
            is ByteArray -> mmkv?.encode(key, value)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                mmkv?.encode(key, value as? Set<String>)
            }
            else -> {
                logE(message = "MMKVUtils: Unsupported type for key: $key")
            }
        }
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return mmkv?.decodeString(key, defaultValue)
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return mmkv?.decodeInt(key, defaultValue) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return mmkv?.decodeBool(key, defaultValue) ?: defaultValue
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return mmkv?.decodeFloat(key, defaultValue) ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return mmkv?.decodeLong(key, defaultValue) ?: defaultValue
    }

    fun getByteArray(key: String, defaultValue: ByteArray? = null): ByteArray? {
        return mmkv?.decodeBytes(key, defaultValue)
    }

    fun getStringSet(key: String, defaultValue: Set<String>? = null): Set<String>? {
        return mmkv?.decodeStringSet(key, defaultValue)
    }

    fun remove(key: String) {
        mmkv?.removeValueForKey(key)
    }

    fun removeValues(keys: Array<String>) {
        mmkv?.removeValuesForKeys(keys)
    }

    fun contains(key: String): Boolean {
        return mmkv?.containsKey(key) ?: false
    }

    fun clearAll() {
        mmkv?.clearAll()
    }
}