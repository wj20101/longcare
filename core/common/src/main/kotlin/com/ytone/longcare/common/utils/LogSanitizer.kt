package com.ytone.longcare.common.utils

/** Redaction shared by local and remote diagnostics. */
object LogSanitizer {
    fun sanitize(raw: String): String {
        var sanitized = raw
        val faceImageQuotedValueRegex = Regex(
            pattern = """(?i)(["']?face[_-]?img(?:[_-]?url)?["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*')"""
        )
        sanitized = faceImageQuotedValueRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}\"***\""
        }

        val quotedCredentialRegex = Regex(
            pattern = """(?i)(["']?(?:password|pwd|token|access_token|refresh_token|secret|api[_-]?key|authorization)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*')"""
        )
        sanitized = quotedCredentialRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}\"***\""
        }

        val sensitiveKeyValueRegex = Regex(
            pattern = """(?i)(["']?(?:password|pwd|token|access_token|refresh_token|secret|api[_-]?key|authorization|face[_-]?img(?:[_-]?url)?)["']?\s*[:=]\s*["']?)([^"',\s}]+)(["']?)"""
        )
        sanitized = sensitiveKeyValueRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}***${match.groupValues[3]}"
        }

        val bearerTokenRegex = Regex("""(?i)(bearer\s+)([A-Za-z0-9\-._~+/]+=*)""")
        sanitized = bearerTokenRegex.replace(sanitized) { match ->
            "${match.groupValues[1]}***"
        }

        val phoneRegex = Regex("""(?<!\d)1\d{10}(?!\d)""")
        sanitized = phoneRegex.replace(sanitized) { match ->
            val phone = match.value
            "${phone.substring(0, 3)}****${phone.substring(7)}"
        }

        val emailRegex = Regex("""(?i)\b([A-Z0-9._%+-]{1,64})@([A-Z0-9.-]+\.[A-Z]{2,})\b""")
        sanitized = emailRegex.replace(sanitized) { match ->
            val localPart = match.groupValues[1]
            val domain = match.groupValues[2]
            val maskedLocal = when {
                localPart.length <= 1 -> "*"
                localPart.length == 2 -> "${localPart[0]}*"
                else -> "${localPart.take(1)}***${localPart.takeLast(1)}"
            }
            "$maskedLocal@$domain"
        }

        val idCardRegex = Regex("""(?i)(?<![0-9A-Z])[1-9]\d{16}[0-9X](?![0-9A-Z])""")
        sanitized = idCardRegex.replace(sanitized) { match ->
            val id = match.value
            "${id.take(6)}********${id.takeLast(4)}"
        }

        return sanitized
    }

}
