package com.ytone.longcare.model

fun User.userIdentityShow(): String {
    return when (userIdentity) {
        1 -> "护理员"
        else -> "其他"
    }
}
