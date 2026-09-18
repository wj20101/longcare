package com.ytone.longcare.features.photoupload.ui

enum class DelayMode(val seconds: Int) {
    OFF(0),
    SECONDS_3(3),
    SECONDS_5(5),
    SECONDS_10(10);

    fun next(): DelayMode {
        val values = entries.toTypedArray()
        return values[(ordinal + 1) % values.size]
    }
}
