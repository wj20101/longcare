package com.ytone.longcare.features.photoupload.ui

enum class DelayMode(val seconds: Int, val label: String) {
    OFF(0, "关闭"),
    SECONDS_3(3, "3秒"),
    SECONDS_5(5, "5秒"),
    SECONDS_10(10, "10秒");

    fun next(): DelayMode {
        val values = entries.toTypedArray()
        return values[(ordinal + 1) % values.size]
    }
}
