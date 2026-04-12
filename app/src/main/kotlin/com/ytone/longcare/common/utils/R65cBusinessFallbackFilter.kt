package com.ytone.longcare.common.utils

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal sealed class R65cBusinessFallbackResult {
    data class Valid(val tagId: String) : R65cBusinessFallbackResult()
    data class DuplicateSuppressed(val tagId: String) : R65cBusinessFallbackResult()
    data class Invalid(val streak: Int) : R65cBusinessFallbackResult()
    data class DeviceError(val streak: Int) : R65cBusinessFallbackResult()
}

@Singleton
public class R65cBusinessFallbackFilter @Inject constructor() {
    private var nowProvider: () -> Long = System::currentTimeMillis
    private var duplicateWindowMillis: Long = 1500L
    private var invalidThreshold: Int = 3

    internal constructor(
        nowProvider: () -> Long,
        duplicateWindowMillis: Long,
        invalidThreshold: Int,
    ) : this() {
        this.nowProvider = nowProvider
        this.duplicateWindowMillis = duplicateWindowMillis
        this.invalidThreshold = invalidThreshold
    }

    private var invalidStreak: Int = 0
    private var lastPublishedTagId: String? = null
    private var lastPublishedAtMillis: Long = 0L

    internal fun consume(rawPayload: String): R65cBusinessFallbackResult {
        val normalized = normalize(rawPayload)
        if (normalized == null) {
            invalidStreak += 1
            return if (invalidStreak >= invalidThreshold) {
                R65cBusinessFallbackResult.DeviceError(invalidStreak)
            } else {
                R65cBusinessFallbackResult.Invalid(invalidStreak)
            }
        }

        val now = nowProvider()
        if (normalized == lastPublishedTagId && now - lastPublishedAtMillis <= duplicateWindowMillis) {
            invalidStreak = 0
            return R65cBusinessFallbackResult.DuplicateSuppressed(normalized)
        }

        invalidStreak = 0
        lastPublishedTagId = normalized
        lastPublishedAtMillis = now
        return R65cBusinessFallbackResult.Valid(normalized)
    }

    private fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(Regex("[\\s:_-]+"), "")
            .uppercase(Locale.ROOT)

        val isHex = normalized.isNotBlank() && normalized.all { it in '0'..'9' || it in 'A'..'F' }
        val hasValidLength = normalized.length == 8 || normalized.length == 14
        return normalized.takeIf { isHex && hasValidLength }
    }
}
