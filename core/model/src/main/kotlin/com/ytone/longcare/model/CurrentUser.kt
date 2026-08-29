package com.ytone.longcare.model

/**
 * Public, immutable view of the active user.
 *
 * Session secrets such as the token and identity card number must stay in the data layer.
 */
data class CurrentUser(
    val scopeKey: UserScopeKey,
    val userName: String,
    val headUrl: String,
    val userIdentity: Int,
    val gender: Int,
) {
    val companyId: Int get() = scopeKey.companyId
    val accountId: Int get() = scopeKey.accountId
    val userId: Int get() = scopeKey.userId
}

fun User.toCurrentUser(): CurrentUser = CurrentUser(
    scopeKey = requireScopeKey(),
    userName = userName,
    headUrl = headUrl,
    userIdentity = userIdentity,
    gender = gender,
)
