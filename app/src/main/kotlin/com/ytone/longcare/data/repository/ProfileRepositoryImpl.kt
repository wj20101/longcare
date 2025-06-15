package com.ytone.longcare.data.repository

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val userSessionRepository: UserSessionRepository
) : ProfileRepository {

    override suspend fun logout(): ApiResult<Unit> {
        userSessionRepository.logoutUser()
        return ApiResult.Success(Unit)
    }
}