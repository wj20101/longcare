package com.ytone.longcare.common.utils

<<<<<<< HEAD
import java.util.Locale
=======
>>>>>>> 1d86300 (feat: add NFC fallback scan contracts)
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRfidTagParser @Inject constructor() {
    fun normalize(rawPayload: String): String? {
        val normalized = rawPayload
            .trim()
<<<<<<< HEAD
            .replace(Regex("\\s+"), "")
            .uppercase(Locale.ROOT)
=======
            .replace(" ", "")
            .uppercase()
>>>>>>> 1d86300 (feat: add NFC fallback scan contracts)

        return normalized.takeIf {
            it.isNotBlank() && it.all(Char::isLetterOrDigit)
        }
    }
}
