package com.ytone.longcare.model

import com.ytone.longcare.models.protos.User

fun User.userIdentityShow(): String {
    return when (userIdentity) {
        1 -> "护理员"
        else -> "其他"
    }
}