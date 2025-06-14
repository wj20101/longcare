package com.ytone.longcare.domain.profile

import com.ytone.longcare.common.network.ApiResult

interface ProfileRepository {
    suspend fun logout(): ApiResult<Unit>
}