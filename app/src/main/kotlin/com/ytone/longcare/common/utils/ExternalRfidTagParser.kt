package com.ytone.longcare.common.utils

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(Regex("[\\s:_-]+"), "")
            .uppercase(Locale.ROOT)

        return normalized.takeIf {
            it.isNotBlank() &&
                it.all { ch -> ch in '0'..'9' || ch in 'A'..'F' } &&
                (it.length == 8 || it.length == 14)
        }
    }
}
