package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.NurseServiceTimeModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val userSessionRepository: UserSessionRepository,
    private val faceCacheCleaner: FaceCacheCleaner,
) : ProfileRepository {
    override suspend fun getServiceStatistics(): ApiResult<NurseServiceTimeModel> =
        apiService.getServiceStatistics()

    override suspend fun logout(): ApiResult<Unit> {
        val userId = userSessionRepository.sessionState.value.user?.userId
        val result = apiService.logout()
        if (userId != null) {
            try {
                faceCacheCleaner.clearUserFaceBase64(userId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                logE("Failed to clear face cache for user $userId", throwable = throwable)
            }
        }
        userSessionRepository.logout()
        return result
    }
}
