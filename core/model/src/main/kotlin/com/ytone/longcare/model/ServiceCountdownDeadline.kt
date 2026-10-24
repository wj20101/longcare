package com.ytone.longcare.model

/** The original wall deadline is a stable reminder identity; the elapsed deadline measures duration. */
data class ServiceCountdownDeadline(
    val wallTimeMillis: Long,
    val elapsedRealtimeMillis: Long? = null,
    val bootCount: Int? = null,
) {
    fun usesMonotonicTime(now: ServiceTimeSnapshot): Boolean =
        elapsedRealtimeMillis != null && bootCount != null && bootCount >= 0 && bootCount == now.bootCount

    fun remainingMillis(now: ServiceTimeSnapshot): Long =
        if (usesMonotonicTime(now)) elapsedRealtimeMillis!! - now.elapsedRealtimeMillis
        else wallTimeMillis - now.wallTimeMillis

    fun triggerAtMillis(now: ServiceTimeSnapshot): Long =
        if (usesMonotonicTime(now)) now.wallTimeMillis + remainingMillis(now) else wallTimeMillis
}
