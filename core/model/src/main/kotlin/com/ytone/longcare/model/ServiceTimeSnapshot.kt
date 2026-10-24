package com.ytone.longcare.model

/** A service time observation; elapsed time is comparable only within a known boot. */
data class ServiceTimeSnapshot(
    val wallTimeMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bootCount: Int?,
)
