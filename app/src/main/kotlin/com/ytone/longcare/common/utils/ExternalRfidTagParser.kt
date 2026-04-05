package com.ytone.longcare.common.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
            .replace(" ", "")
            .uppercase()

        return normalized.takeIf {
            it.isNotBlank() && it.all(Char::isLetterOrDigit)
        }
    }
}
