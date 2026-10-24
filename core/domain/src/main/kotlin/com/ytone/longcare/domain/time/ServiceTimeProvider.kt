package com.ytone.longcare.domain.time

import com.ytone.longcare.model.ServiceTimeSnapshot

fun interface ServiceTimeProvider {
    fun snapshot(): ServiceTimeSnapshot
}
