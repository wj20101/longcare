package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.response.NurseServiceTimeModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val userSessionRepository: UserSessionRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : ProfileRepository {
    override suspend fun getServiceStatistics(): ApiResult<NurseServiceTimeModel> {
        return safeApiCall(ioDispatcher, eventBus) { apiService.getServiceStatistics() }
    }

    override suspend fun logout(): ApiResult<Unit> {
        userSessionRepository.logout()
        return ApiResult.Success(Unit)
    }
}