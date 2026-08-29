package com.ytone.longcare.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val USER_SCOPE_FORMAT_VERSION = "v1"
private const val USER_SCOPE_DOMAIN = "longcare-user-scope"
private val namespacePattern = Regex("v1_[0-9a-f]{64}")

/**
 * Stable ownership key for every persisted user resource.
 *
 * Authentication and presentation fields intentionally do not participate in ownership.
 */
data class UserScopeKey(
    val companyId: Int,
    val accountId: Int,
    val userId: Int,
) {
    init {
        require(companyId > 0) { "companyId must be positive" }
        require(accountId > 0) { "accountId must be positive" }
        require(userId > 0) { "userId must be positive" }
    }

    fun canonicalBytes(): ByteArray = buildString {
        append(USER_SCOPE_DOMAIN)
        append('\n')
        append(USER_SCOPE_FORMAT_VERSION)
        append('\n')
        appendCanonicalComponent("companyId", companyId.toString())
        appendCanonicalComponent("accountId", accountId.toString())
        appendCanonicalComponent("userId", userId.toString())
    }.toByteArray(StandardCharsets.UTF_8)

    fun namespaceId(): NamespaceId {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes())
        return NamespaceId("${USER_SCOPE_FORMAT_VERSION}_${digest.toHexString()}")
    }

    override fun toString(): String = "UserScopeKey(namespace=${namespaceId().value})"
}

@JvmInline
value class NamespaceId(val value: String) {
    init {
        require(namespacePattern.matches(value)) { "Invalid namespace id" }
    }

    override fun toString(): String = value
}

fun User.requireScopeKey(): UserScopeKey = UserScopeKey(
    companyId = companyId,
    accountId = accountId,
    userId = userId,
)

private fun StringBuilder.appendCanonicalComponent(name: String, value: String) {
    val byteLength = value.toByteArray(StandardCharsets.UTF_8).size
    append(name)
    append(':')
    append(byteLength)
    append(':')
    append(value)
    append('\n')
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
