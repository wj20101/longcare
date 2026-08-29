package com.ytone.longcare.model

/**
 * Write-only payload accepted at the login boundary.
 *
 * It must never be retained by UI state, navigation arguments, workers, or ordinary features.
 * After activation, public consumers receive only [CurrentUser].
 */
data class SessionLoginPayload(
    val companyId: Int,
    val accountId: Int,
    val userId: Int,
    val userName: String,
    val headUrl: String,
    val userIdentity: Int,
    val identityCardNumber: String,
    val gender: Int,
    val token: String,
) {
    init {
        UserScopeKey(companyId, accountId, userId)
        require(token.isNotBlank()) { "Session token must not be blank" }
    }

    val scopeKey: UserScopeKey get() = UserScopeKey(companyId, accountId, userId)

    fun toCurrentUser(): CurrentUser = CurrentUser(
        scopeKey = scopeKey,
        userName = userName,
        headUrl = headUrl,
        userIdentity = userIdentity,
        gender = gender,
    )
}

fun User.toSessionLoginPayload(): SessionLoginPayload = SessionLoginPayload(
    companyId = companyId,
    accountId = accountId,
    userId = userId,
    userName = userName,
    headUrl = headUrl,
    userIdentity = userIdentity,
    identityCardNumber = identityCardNumber,
    gender = gender,
    token = token,
)

fun SessionLoginPayload.toUser(): User = User(
    companyId = companyId,
    accountId = accountId,
    userId = userId,
    userName = userName,
    headUrl = headUrl,
    userIdentity = userIdentity,
    identityCardNumber = identityCardNumber,
    gender = gender,
    token = token,
)
