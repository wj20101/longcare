package com.ytone.longcare.model

fun User.userIdentityShow(): String {
    return when (userIdentity) {
        1 -> "护理员"
        2 -> "销售员"
        else -> "其他"
    }
}
