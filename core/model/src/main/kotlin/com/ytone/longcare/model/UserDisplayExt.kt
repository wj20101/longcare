package com.ytone.longcare.model

enum class UserIdentityDisplay {
    CAREGIVER,
    SALES,
    OTHER,
}

fun User.userIdentityDisplay(): UserIdentityDisplay {
    return when (userIdentity) {
        1 -> UserIdentityDisplay.CAREGIVER
        2 -> UserIdentityDisplay.SALES
        else -> UserIdentityDisplay.OTHER
    }
}

fun CurrentUser.userIdentityDisplay(): UserIdentityDisplay {
    return when (userIdentity) {
        1 -> UserIdentityDisplay.CAREGIVER
        2 -> UserIdentityDisplay.SALES
        else -> UserIdentityDisplay.OTHER
    }
}
