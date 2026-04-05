package com.ytone.longcare.common.utils

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(Regex("\\s+"), "")
            .uppercase(Locale.ROOT)

        return normalized.takeIf {
            it.isNotBlank() && it.all(Char::isLetterOrDigit)
        }
    }
}
